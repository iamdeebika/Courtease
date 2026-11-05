import os

from sqlalchemy import select

from b3 import UPLOAD_DIR
from database import Base, SessionLocal, engine
from integrity import DocFingerprint, sha256_file
from models import Document, utcnow

Base.metadata.create_all(bind=engine)
db = SessionLocal()

have = {row[0] for row in db.execute(select(DocFingerprint.document_id))}
added = skipped = missing = 0
for d in db.scalars(select(Document)):
    if not d.stored_name:
        continue
    if d.id in have:
        skipped += 1
        continue
    digest, size = sha256_file(os.path.join(UPLOAD_DIR, d.stored_name))
    if not digest:
        missing += 1
        print("File missing for:", d.name)
        continue
    db.add(DocFingerprint(
        document_id=d.id, sha256=digest, size_bytes=size, origin="backfill", created_at=utcnow(),
    ))
    added += 1
db.commit()
db.close()
print("Fingerprinted %d file(s). Already had one: %d. File missing: %d." % (added, skipped, missing))