from sqlalchemy import select

from database import SessionLocal
from models import Document

db = SessionLocal()
rows = db.scalars(
    select(Document)
    .where(Document.filed.is_(True), Document.stored_name != "")
    .order_by(Document.id.desc())
    .limit(3)
)
print("Filed documents with a stored file (newest first):")
for d in rows:
    print(" ", d.id, "|", d.name, "|", d.stored_name)
db.close()