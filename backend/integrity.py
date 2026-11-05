import hashlib
import hmac
import os
from datetime import datetime

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import DateTime, Integer, String, event, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from b3 import UPLOAD_DIR
from database import Base, get_db
from models import Case, Document, User, utcnow


class DocFingerprint(Base):
    __tablename__ = "doc_fingerprints"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(Integer, unique=True, index=True)
    sha256: Mapped[str] = mapped_column(String(64))
    size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    origin: Mapped[str] = mapped_column(String(10), default="upload")  # upload or backfill
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class IntegrityCheck(BaseModel):
    doc_id: int
    status: str  # verified, mismatch, missing, unrecorded or nofile
    message: str
    code: str
    sha256: str
    origin: str
    recorded_at: int
    size: int


def sha256_file(path):
    """Returns (hex digest, size in bytes), or ("", 0) when the file can't be read."""
    try:
        h = hashlib.sha256()
        total = 0
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b""):
                h.update(chunk)
                total += len(chunk)
        return h.hexdigest(), total
    except OSError:
        return "", 0


def short_code(sha):
    sha = (sha or "").upper()
    return "-".join(sha[i:i + 4] for i in (0, 4, 8)) if sha else ""


def _record_on_insert(mapper, connection, target):
    """Runs right after a Document row is saved. The uploaded file is already on disk by then."""
    if not target.stored_name:
        return
    digest, size = sha256_file(os.path.join(UPLOAD_DIR, target.stored_name))
    if not digest:
        return
    table = DocFingerprint.__table__
    connection.execute(table.delete().where(table.c.document_id == target.id))
    connection.execute(table.insert().values(
        document_id=target.id, sha256=digest, size_bytes=size, origin="upload", created_at=utcnow(),
    ))


def install_integrity(app, m):
    """Adds the integrity check route. 'm' is main.py, which owns the shared helpers."""
    event.listen(Document, "after_insert", _record_on_insert)

    def case_for(db: Session, user: User, cid: int) -> Case:
        if user.role == "registry":
            raise HTTPException(status_code=403, detail="The registry can't open document contents")
        c = m.get_case(db, cid)
        if user.role == "lawyer" and m.side_of(db, c.id, user.id) is None:
            raise HTTPException(status_code=404, detail="Case not found")
        if user.role == "judge" and c.judge_id != user.id:
            raise HTTPException(status_code=404, detail="Case not found")
        return c

    def can_see(db: Session, user: User, c: Case, d: Document) -> bool:
        if user.role == "lawyer":
            side = m.side_of(db, c.id, user.id)
            return side is not None and (d.filed or d.side == side)
        if user.role == "judge":
            return c.judge_id == user.id and d.filed and c.status in m.JUDGE_SEES
        return False

    @app.get("/docs/{did}/integrity", response_model=IntegrityCheck)
    def check_integrity(did: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        d = db.get(Document, did)
        if d is None:
            raise HTTPException(status_code=404, detail="Document not found")
        c = case_for(db, user, d.case_id)
        if not can_see(db, user, c, d):
            raise HTTPException(status_code=404, detail="Document not found")

        fp = db.scalar(select(DocFingerprint).where(DocFingerprint.document_id == d.id))
        code = short_code(fp.sha256) if fp else ""
        base = dict(
            doc_id=d.id, code=code, sha256=fp.sha256 if fp else "", origin=fp.origin if fp else "",
            recorded_at=m.millis(fp.created_at) if fp else 0, size=fp.size_bytes if fp else 0,
        )
        if not d.stored_name:
            return IntegrityCheck(
                status="nofile", message="This entry has no file attached, so there is nothing to check.", **base
            )

        digest, _size = sha256_file(os.path.join(UPLOAD_DIR, d.stored_name))
        if not digest:
            m.audit(db, user.id, "Integrity check failed", f"{m.label(c)} · {d.name} · file missing")
            db.commit()
            return IntegrityCheck(status="missing", message="The stored file is missing on the server.", **base)
        if fp is None:
            return IntegrityCheck(
                status="unrecorded",
                message="No fingerprint was recorded for this file yet. Run the fingerprint step on the server.",
                **base,
            )
        if hmac.compare_digest(digest, fp.sha256):
            return IntegrityCheck(
                status="verified",
                message="The file on the server matches the fingerprint that was recorded for it.",
                **base,
            )
        m.audit(db, user.id, "Integrity check failed", f"{m.label(c)} · {d.name} · file changed")
        db.commit()
        return IntegrityCheck(
            status="mismatch",
            message="The file on the server does not match the fingerprint that was recorded for it.",
            **base,
        )