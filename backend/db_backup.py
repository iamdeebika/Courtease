"""Encrypted backup and restore for the CourtEase database and the uploaded files.

  python db_backup.py backup
  python db_backup.py verify  <file.cebak>
  python db_backup.py restore <file.cebak>

It reads the database through the same connection the app uses, so it needs no mysqldump.
"""
import io
import os
import re
import shutil
import sys
import tarfile
from base64 import urlsafe_b64encode
from datetime import datetime

from cryptography.fernet import Fernet, InvalidToken
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from dotenv import load_dotenv

HERE = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(HERE, ".env"))
sys.path.insert(0, HERE)

from database import engine  # noqa: E402

UPLOADS = os.path.join(HERE, "uploads")
BACKUP_DIR = os.getenv("BACKUP_DIR", os.path.join(os.path.expanduser("~"), "CourtEaseBackups"))
MAGIC = b"CEBK1"
ITERATIONS = 480000
BATCH = 100


def raw_connection():
    fairy = engine.raw_connection()
    conn = getattr(fairy, "driver_connection", None) or fairy.connection
    return fairy, conn


def key_for(passphrase, salt):
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=ITERATIONS)
    return urlsafe_b64encode(kdf.derive(passphrase.encode("utf-8")))


def ask(confirm):
    print("Note: what you type here is VISIBLE on the screen. Make sure nobody is looking.")
    first = input("Backup passphrase (12 or more characters): ").strip()
    if confirm:
        if len(first) < 12:
            sys.exit("Use at least 12 characters.")
        again = input("Type it again: ").strip()
        if again != first:
            sys.exit("The two entries are different.")
    return first


def open_backup(path):
    with open(path, "rb") as f:
        data = f.read()
    if not data.startswith(MAGIC):
        sys.exit("This is not a CourtEase backup file.")
    salt = data[len(MAGIC):len(MAGIC) + 16]
    token = data[len(MAGIC) + 16:]
    passphrase = ask(False)
    try:
        raw = Fernet(key_for(passphrase, salt)).decrypt(token)
    except InvalidToken:
        sys.exit("Wrong passphrase, or the file is damaged.")
    return tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz")


def dump_sql():
    """Returns the whole database as SQL text, plus the number of tables and rows."""
    fairy, conn = raw_connection()
    cur = conn.cursor()
    parts = []
    rows_total = 0
    names = []
    try:
        cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT")
        cur.execute("SHOW TABLES")
        names = [r[0] for r in cur.fetchall()]
        for name in names:
            cur.execute("SHOW CREATE TABLE `%s`" % name)
            create = cur.fetchone()[1]
            parts.append("DROP TABLE IF EXISTS `%s`;\n" % name)
            parts.append(create + ";\n")
            cur.execute("SELECT * FROM `%s`" % name)
            cols = ",".join("`%s`" % d[0] for d in cur.description)
            while True:
                rows = cur.fetchmany(BATCH)
                if not rows:
                    break
                values = []
                for row in rows:
                    values.append("(" + ",".join(conn.escape(v) for v in row) + ")")
                parts.append("INSERT INTO `%s` (%s) VALUES %s;\n" % (name, cols, ",".join(values)))
                rows_total += len(rows)
        conn.rollback()
    finally:
        cur.close()
        fairy.close()
    return "".join(parts), len(names), rows_total


def backup():
    os.makedirs(BACKUP_DIR, exist_ok=True)
    passphrase = ask(True)
    sql, table_count, row_count = dump_sql()

    buf = io.BytesIO()
    files = 0
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        data = sql.encode("utf-8")
        info = tarfile.TarInfo("courtease.sql")
        info.size = len(data)
        tar.addfile(info, io.BytesIO(data))
        if os.path.isdir(UPLOADS):
            for name in sorted(os.listdir(UPLOADS)):
                full = os.path.join(UPLOADS, name)
                if os.path.isfile(full):
                    tar.add(full, arcname="uploads/" + name)
                    files += 1
    salt = os.urandom(16)
    token = Fernet(key_for(passphrase, salt)).encrypt(buf.getvalue())
    out = os.path.join(BACKUP_DIR, datetime.now().strftime("courtease_%Y%m%d_%H%M.cebak"))
    with open(out, "wb") as f:
        f.write(MAGIC + salt + token)
    print("Backup written: " + out)
    print("Tables: %d, rows: %d, uploaded files: %d" % (table_count, row_count, files))
    print("Size: %d KB" % (os.path.getsize(out) // 1024))
    print("Keep the passphrase safe. Without it the backup cannot be opened.")


def verify(path):
    tar = open_backup(path)
    sql = tar.extractfile("courtease.sql").read().decode("utf-8", "replace")
    tables = re.findall(r"CREATE TABLE `([^`]+)`", sql)
    inserts = len(re.findall(r"^INSERT INTO ", sql, flags=re.M))
    files = [m for m in tar.getmembers() if m.isfile() and m.name.startswith("uploads/")]
    print("Passphrase accepted. The backup opens correctly.")
    print("Tables (%d): %s" % (len(tables), ", ".join(tables)))
    print("Insert statements: %d" % inserts)
    print("Uploaded files: %d" % len(files))
    print("Dump size: %d KB" % (len(sql) // 1024))


def restore(path):
    tar = open_backup(path)
    sql = tar.extractfile("courtease.sql").read().decode("utf-8")
    print("This REPLACES every table in the current database and the uploaded files.")
    print("If it stops half way the data can be left incomplete, so make a fresh backup first.")
    if input("Type RESTORE to continue: ").strip() != "RESTORE":
        sys.exit("Cancelled. Nothing was changed.")
    fairy, conn = raw_connection()
    cur = conn.cursor()
    try:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        count = 0
        for stmt in sql.split(";\n"):
            if stmt.strip():
                cur.execute(stmt)
                count += 1
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
        conn.commit()
    except Exception as exc:
        conn.rollback()
        sys.exit("Restore failed: %s" % exc)
    finally:
        cur.close()
        fairy.close()
    os.makedirs(UPLOADS, exist_ok=True)
    files = 0
    for m in tar.getmembers():
        if m.isfile() and m.name.startswith("uploads/"):
            target = os.path.join(UPLOADS, os.path.basename(m.name))
            with open(target, "wb") as f:
                f.write(tar.extractfile(m).read())
            files += 1
    print("Restored %d statements and %d uploaded file(s). Restart the server." % (count, files))


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["backup"]:
        backup()
    elif len(args) == 2 and args[0] == "verify":
        verify(args[1])
    elif len(args) == 2 and args[0] == "restore":
        restore(args[1])
    else:
        print(__doc__)