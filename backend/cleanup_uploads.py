import os
import sys

from sqlalchemy import select

from b3 import UPLOAD_DIR
from database import SessionLocal
from models import Document

apply = "--apply" in sys.argv

db = SessionLocal()
used = {name for (name,) in db.execute(select(Document.stored_name)) if name}
db.close()

if not os.path.isdir(UPLOAD_DIR):
    raise SystemExit("No uploads folder yet.")

orphans = [f for f in os.listdir(UPLOAD_DIR) if f not in used]
if not orphans:
    print("No orphaned files.")
for name in orphans:
    path = os.path.join(UPLOAD_DIR, name)
    if apply:
        os.remove(path)
        print("Deleted", name)
    else:
        print("Would delete", name)
if orphans and not apply:
    print("Run again with --apply to delete them.")