import re
import time
from datetime import datetime, timezone
from typing import List

from fastapi import Depends, FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from database import Base, engine, get_db
from models import AuditLog, Notification, User
from schemas import (
    ApproveIn, AuditOut, LoginIn, MessageOut, NotifOut, PasswordChangeIn, ProfileIn,
    ReasonIn, ResetPasswordIn, SignupIn, TokenOut, UserOut,
)
from security import create_token, decode_token, hash_secret, verify_secret

Base.metadata.create_all(bind=engine)

app = FastAPI(title="CourtEase API", version="3.0")
bearer = HTTPBearer(auto_error=False)

COURT_ROOMS = {
    "Civil Court, Chennai": ["Court Hall 1", "Court Hall 2", "Court Hall 3"],
    "Civil Court, Bengaluru": ["Court Hall 1", "Court Hall 2"],
    "District Court, Madurai": ["Court Hall 1", "Court Hall 2", "Court Hall 3"],
    "District Court, Coimbatore": ["Court Hall 1", "Court Hall 2"],
}

# 5 wrong password-reset tries per email in 10 minutes.
_reset_failures: dict = {}


def _too_many_attempts(email: str) -> bool:
    now = time.time()
    recent = [t for t in _reset_failures.get(email, []) if now - t < 600]
    _reset_failures[email] = recent
    return len(recent) >= 5


@app.exception_handler(RequestValidationError)
async def validation_handler(request, exc: RequestValidationError):
    first = exc.errors()[0]
    message = str(first.get("msg", "Invalid input")).replace("Value error, ", "")
    return JSONResponse(status_code=422, content={"detail": message})


# ---------- helpers ----------

def millis(dt: datetime) -> int:
    return int(dt.replace(tzinfo=timezone.utc).timestamp() * 1000)


def user_out(u: User, full: bool = True) -> UserOut:
    # "full" is for the person themselves and for the registry.
    # Everyone else gets a short directory entry.
    return UserOut(
        id=u.id,
        full_name=u.full_name,
        email=u.email if (full or u.role != "registry") else "",
        phone=u.phone if full else "",
        role=u.role,
        status=u.status,
        reg_no=u.reg_no if full else "",
        council=u.council if full else "",
        registry_id=u.registry_id or "",
        court=u.court,
        courtroom=u.courtroom,
        reject_reason=u.reject_reason if full else "",
        created_at=millis(u.created_at),
    )


def audit(db: Session, user_id, action: str, detail: str = "") -> None:
    db.add(AuditLog(user_id=user_id, action=action, detail=detail[:255]))


def notify(db: Session, user_id: int, title: str, body: str, case_id: int = 0) -> None:
    db.add(Notification(user_id=user_id, title=title[:160], body=body[:255], case_id=case_id))


def digits(text: str) -> str:
    return re.sub(r"\D", "", text)[-10:]


def next_registry_id(db: Session, role: str) -> str:
    prefix = "JDG" if role == "judge" else "ADV"
    year = datetime.now().year
    n = (db.scalar(
        select(func.count()).select_from(User).where(User.role == role, User.registry_id.is_not(None))
    ) or 0) + 1
    while True:
        rid = f"{prefix}-{year}-{n:04d}"
        if not db.scalar(select(User.id).where(User.registry_id == rid)):
            return rid
        n += 1


def current_user(
        creds: HTTPAuthorizationCredentials = Depends(bearer),
        db: Session = Depends(get_db),
) -> User:
    if creds is None:
        raise HTTPException(status_code=401, detail="Not signed in")
    user_id = decode_token(creds.credentials)
    user = db.get(User, user_id) if user_id else None
    if user is None:
        raise HTTPException(status_code=401, detail="Session expired")
    return user


def approved_user(user: User = Depends(current_user)) -> User:
    if user.status != "approved":
        raise HTTPException(status_code=403, detail="Your account is not approved")
    return user


def registry_user(user: User = Depends(current_user)) -> User:
    if user.role != "registry" or user.status != "approved":
        raise HTTPException(status_code=403, detail="Registry access only")
    return user


# ---------- accounts ----------

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/auth/signup", response_model=UserOut, status_code=201)
def signup(body: SignupIn, db: Session = Depends(get_db)):
    email = body.email.lower()
    existing = db.scalar(select(User).where(User.email == email))
    if existing is not None and existing.status != "rejected":
        raise HTTPException(status_code=409, detail="This email is already registered")

    clash = db.scalar(
        select(User).where(
            User.role == body.role,
            func.lower(User.reg_no) == body.reg_no.lower(),
            User.id != (existing.id if existing else 0),
            )
    )
    if clash is not None:
        raise HTTPException(
            status_code=409,
            detail="This bar number is already registered" if body.role == "lawyer"
            else "This employee ID is already registered",
        )

    if existing is not None:  # a rejected user applying again
        user = existing
        user.full_name, user.phone, user.role = body.full_name, body.phone, body.role
        user.reg_no, user.council = body.reg_no, body.council
        user.password_hash = hash_secret(body.password)
        user.status, user.reject_reason = "pending", ""
    else:
        user = User(
            full_name=body.full_name, email=email, phone=body.phone, role=body.role,
            reg_no=body.reg_no, council=body.council, status="pending",
            password_hash=hash_secret(body.password),
        )
        db.add(user)
        db.flush()

    audit(db, user.id, "Account request", f"{body.role}: {email}")
    for reg in db.scalars(select(User).where(User.role == "registry", User.status == "approved")):
        notify(db, reg.id, "New account request", f"{user.full_name} applied as {body.role.title()}")
    db.commit()
    db.refresh(user)
    return user_out(user)


@app.post("/auth/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    if user is None or not verify_secret(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Wrong email or password")
    # Pending, rejected and suspended users can sign in so the app can show their status.
    return TokenOut(access_token=create_token(user.id), user=user_out(user))


@app.post("/auth/reset-password", response_model=MessageOut)
def reset_password(body: ResetPasswordIn, db: Session = Depends(get_db)):
    email = body.email.lower()
    if _too_many_attempts(email):
        raise HTTPException(status_code=429, detail="Too many attempts. Try again in 10 minutes")
    user = db.scalar(select(User).where(User.email == email))
    if user is None or digits(user.phone) != digits(body.phone):
        _reset_failures.setdefault(email, []).append(time.time())
        raise HTTPException(status_code=400, detail="Email and phone don't match our records")
    user.password_hash = hash_secret(body.new_password)
    audit(db, user.id, "Password reset", user.email)
    db.commit()
    _reset_failures.pop(email, None)
    return MessageOut(message="Password updated")


@app.get("/users/me", response_model=UserOut)
def me(user: User = Depends(current_user)):
    return user_out(user)


@app.put("/users/me", response_model=UserOut)
def update_me(body: ProfileIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    user.full_name = body.full_name
    user.phone = body.phone
    audit(db, user.id, "Updated profile", user.email)
    db.commit()
    db.refresh(user)
    return user_out(user)


@app.post("/users/me/password", response_model=MessageOut)
def change_password(
        body: PasswordChangeIn, user: User = Depends(current_user), db: Session = Depends(get_db)
):
    if not verify_secret(body.current_password, user.password_hash):
        raise HTTPException(status_code=400, detail="Current password is wrong")
    if body.current_password == body.new_password:
        raise HTTPException(status_code=400, detail="Choose a different password")
    user.password_hash = hash_secret(body.new_password)
    audit(db, user.id, "Changed password", user.email)
    db.commit()
    return MessageOut(message="Password updated")


@app.get("/sync/users", response_model=List[UserOut])
def sync_users(user: User = Depends(approved_user), db: Session = Depends(get_db)):
    if user.role == "registry":
        return [user_out(u, True) for u in db.scalars(select(User).order_by(User.id))]
    rows = db.scalars(select(User).where(User.status == "approved").order_by(User.id))
    return [user_out(u, False) for u in rows]


# ---------- notifications ----------

@app.get("/notifications", response_model=List[NotifOut])
def list_notifications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(
        select(Notification).where(Notification.user_id == user.id).order_by(Notification.id.desc()).limit(100)
    )
    return [
        NotifOut(id=n.id, title=n.title, body=n.body, case_id=n.case_id,
                 created_at=millis(n.created_at), is_read=n.is_read)
        for n in rows
    ]


@app.post("/notifications/read-all", response_model=MessageOut)
def read_all(user: User = Depends(current_user), db: Session = Depends(get_db)):
    for n in db.scalars(select(Notification).where(Notification.user_id == user.id, Notification.is_read.is_(False))):
        n.is_read = True
    db.commit()
    return MessageOut(message="Done")


@app.post("/notifications/{nid}/read", response_model=MessageOut)
def read_one(nid: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    n = db.get(Notification, nid)
    if n is not None and n.user_id == user.id:
        n.is_read = True
        db.commit()
    return MessageOut(message="Done")


# ---------- registry ----------

def pending_target(db: Session, uid: int) -> User:
    target = db.get(User, uid)
    if target is None:
        raise HTTPException(status_code=404, detail="Account not found")
    if target.status != "pending":
        raise HTTPException(status_code=400, detail="This request is already decided")
    return target


@app.post("/registry/accounts/{uid}/approve", response_model=UserOut)
def approve(uid: int, body: ApproveIn, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    target = pending_target(db, uid)
    if target.role == "judge":
        rooms = COURT_ROOMS.get(body.court)
        if not rooms or body.courtroom not in rooms:
            raise HTTPException(status_code=400, detail="Choose the judge's court and courtroom")
        target.court, target.courtroom = body.court, body.courtroom
    rid = next_registry_id(db, target.role)
    target.status = "approved"
    target.registry_id = rid
    target.reject_reason = ""
    notify(db, target.id, "Account approved", f"Your registry ID is {rid}")
    audit(db, reg.id, "Approved account", f"{target.email} → {rid}")
    db.commit()
    db.refresh(target)
    return user_out(target)


@app.post("/registry/accounts/{uid}/reject", response_model=UserOut)
def reject(uid: int, body: ReasonIn, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    target = pending_target(db, uid)
    target.status = "rejected"
    target.reject_reason = body.reason
    notify(db, target.id, "Request rejected", body.reason)
    audit(db, reg.id, "Rejected account", f"{target.email}: {body.reason}")
    db.commit()
    db.refresh(target)
    return user_out(target)


@app.post("/registry/accounts/{uid}/suspend", response_model=UserOut)
def suspend(uid: int, body: ReasonIn, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    target = db.get(User, uid)
    if target is None:
        raise HTTPException(status_code=404, detail="Account not found")
    if target.role == "registry" or target.id == reg.id:
        raise HTTPException(status_code=400, detail="This account can't be changed")
    if target.status != "approved":
        raise HTTPException(status_code=400, detail="Only approved accounts can be suspended")
    target.status = "suspended"
    target.reject_reason = body.reason
    notify(db, target.id, "Account suspended", body.reason)
    audit(db, reg.id, "Suspended account", f"{target.email}: {body.reason}")
    db.commit()
    db.refresh(target)
    return user_out(target)


@app.post("/registry/accounts/{uid}/reinstate", response_model=UserOut)
def reinstate(uid: int, body: dict = None, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    target = db.get(User, uid)
    if target is None:
        raise HTTPException(status_code=404, detail="Account not found")
    if target.status != "suspended":
        raise HTTPException(status_code=400, detail="This account is not suspended")
    target.status = "approved"
    target.reject_reason = ""
    notify(db, target.id, "Account reinstated", "You can use your account again")
    audit(db, reg.id, "Reinstated account", target.email)
    db.commit()
    db.refresh(target)
    return user_out(target)


@app.get("/registry/audit", response_model=List[AuditOut])
def audit_log(reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(AuditLog, User.full_name)
        .outerjoin(User, AuditLog.user_id == User.id)
        .order_by(AuditLog.id.desc())
        .limit(200)
    ).all()
    return [
        AuditOut(id=a.id, user_name=name or "System", action=a.action,
                 detail=a.detail, created_at=millis(a.created_at))
        for a, name in rows
    ]