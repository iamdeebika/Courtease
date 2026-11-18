import secrets
import time

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from database import get_db
from law import SavedPrecedent
from models import AuditLog, Case, CaseCounsel, Notification, TeamTask, User
from push import DeviceToken
from security import verify_secret
from summary import AnalysisSeen

HASH_NAMES = ("hash_secret", "hash_password", "get_password_hash", "hash_pw", "make_hash")
_tries = {}


class DeleteIn(BaseModel):
    password: str


def _hasher():
    import security
    for name in HASH_NAMES:
        fn = getattr(security, name, None)
        if callable(fn):
            return fn
    return None


def _private_notes():
    for module in ("models", "b3"):
        try:
            mod = __import__(module)
        except Exception:
            continue
        cls = getattr(mod, "PrivateNote", None)
        if cls is not None:
            return cls
    return None


def install_account_delete(app, m):
    """Adds POST /auth/delete-account. The account is anonymised and personal data is removed."""
    hasher = _hasher()
    if hasher is None:
        print("Account deletion is OFF: security.py has no password hashing function I recognise. Send me its function names.")
        return
    note_cls = _private_notes()
    who = getattr(m, "current_user", None) or m.approved_user

    @app.post("/auth/delete-account", response_model=m.MessageOut)
    def delete_account(body: DeleteIn, user: User = Depends(who), db: Session = Depends(get_db)):
        now = time.time()
        recent = [t for t in _tries.get(user.id, []) if now - t < 600]
        if len(recent) >= 5:
            raise HTTPException(status_code=429, detail="Too many wrong passwords. Try again in a few minutes.")
        if user.role == "registry":
            raise HTTPException(status_code=400, detail="The registry account can't be deleted here. Ask the system owner.")
        if not verify_secret(body.password, user.password_hash):
            recent.append(now)
            _tries[user.id] = recent
            raise HTTPException(status_code=400, detail="Wrong password")
        _tries.pop(user.id, None)

        led = list(db.scalars(
            select(Case.id).join(CaseCounsel, CaseCounsel.case_id == Case.id)
            .where(CaseCounsel.user_id == user.id, CaseCounsel.position == 0, Case.status != "closed")
        ))
        judged = list(db.scalars(select(Case.id).where(Case.judge_id == user.id, Case.status != "closed")))
        if led or judged:
            raise HTTPException(
                status_code=400,
                detail="You still have %d open case(s). Close them, or delete your drafts, before deleting your account."
                % len(set(led + judged)),
            )

        old_email = user.email
        old_id = user.id
        for model in (DeviceToken, SavedPrecedent, AnalysisSeen, Notification):
            db.execute(delete(model).where(model.user_id == old_id))
        if note_cls is not None:
            db.execute(delete(note_cls).where(note_cls.user_id == old_id))
        db.execute(delete(TeamTask).where(TeamTask.assignee_id == old_id))
        open_ids = select(Case.id).where(Case.status != "closed")
        db.execute(delete(CaseCounsel).where(
            CaseCounsel.user_id == old_id, CaseCounsel.position > 0, CaseCounsel.case_id.in_(open_ids)
        ))
        db.execute(update(AuditLog).where(AuditLog.detail.contains(old_email)).values(detail="(removed)"))

        user.full_name = "Deleted user"
        user.email = "deleted-%d@deleted.invalid" % old_id
        user.phone = "x" + secrets.token_hex(8)
        user.reg_no = ""
        user.council = ""
        user.registry_id = None
        user.court = ""
        user.courtroom = ""
        user.reject_reason = "Account deleted"
        user.status = "suspended"
        user.password_hash = hasher(secrets.token_urlsafe(32))
        m.audit(db, old_id, "Deleted account", "user #%d" % old_id)
        db.commit()
        return m.MessageOut(message="Your account was deleted")

    print("Account deletion is ready. Private bench notes:", "handled" if note_cls is not None else "NOT found, tell me")
