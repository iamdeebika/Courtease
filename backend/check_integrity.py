import os

from sqlalchemy import select

from b3 import UPLOAD_DIR
from database import SessionLocal
from integrity import DocFingerprint, sha256_file
from models import Document

db = SessionLocal()
prints = {f.document_id: f for f in db.scalars(select(DocFingerprint))}
print("id | name | file | recorded | now | result")
for d in db.scalars(select(Document).where(Document.stored_name != "").order_by(Document.id)):
    f = prints.get(d.id)
    digest, size = sha256_file(os.path.join(UPLOAD_DIR, d.stored_name))
    if not digest:
        result = "FILE MISSING"
    elif f is None:
        result = "NO FINGERPRINT"
    elif digest == f.sha256:
        result = "match"
    else:
        result = "MISMATCH"
    print(d.id, "|", d.name, "|", d.stored_name[:8], "|", (f.sha256[:8] if f else "-"), "|", (digest[:8] or "-"), "|", result, "| size", size)
db.close()