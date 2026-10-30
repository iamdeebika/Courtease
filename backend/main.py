import re
import secrets
import time
from datetime import date, datetime, timedelta, timezone
from typing import List, Optional

from fastapi import Depends, FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from database import Base, engine, get_db
from models import (
    AuditLog, Case, CaseCounsel, Document, Notification, Order, PrivateNote, User, utcnow,
)
from schemas import (
    AllocateIn, ApproveIn, AuditOut, CaseIn, CaseOut, DocIn, DocOut, JoinIn, LoginIn,
    MessageOut, NoteIn, NoteOut, NotifOut, OrderIn, OrderOut, PasswordChangeIn, PreviewOut,
    ProfileIn, ReasonIn, ResetPasswordIn, ReturnIn, SignupIn, SyncOut, TokenOut, UserOut,
)
from security import create_token, decode_token, hash_secret, verify_secret

Base.metadata.create_all(bind=engine)

app = FastAPI(title="CourtEase API", version="4.0")
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


# ---------- registry: accounts ----------

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
def reinstate(uid: int, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
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


# =====================================================================
# Cases, documents, registry flow and judge actions
# =====================================================================

EDITABLE = ("draft", "returned")
JUDGE_SEES = ("allocated", "notice", "pleadings", "review", "judgment", "closed")
DOC_TYPES = {"Plaint", "Vakalatnama", "Written statement", "Evidence", "Annexure", "Other"}


def lawyer_user(user: User = Depends(approved_user)) -> User:
    if user.role != "lawyer":
        raise HTTPException(status_code=403, detail="Advocates only")
    return user


def judge_user(user: User = Depends(approved_user)) -> User:
    if user.role != "judge":
        raise HTTPException(status_code=403, detail="Judges only")
    return user


def get_case(db: Session, cid: int) -> Case:
    c = db.get(Case, cid)
    if c is None:
        raise HTTPException(status_code=404, detail="Case not found")
    return c


def counsel_map(db: Session, case_ids: list) -> dict:
    out = {cid: {"claimant": [], "respondent": []} for cid in case_ids}
    if not case_ids:
        return out
    rows = db.scalars(
        select(CaseCounsel).where(CaseCounsel.case_id.in_(case_ids)).order_by(CaseCounsel.position, CaseCounsel.id)
    )
    for r in rows:
        out[r.case_id][r.side].append(r.user_id)
    return out


def teams_of(db: Session, case_id: int) -> dict:
    return counsel_map(db, [case_id])[case_id]


def side_of(db: Session, case_id: int, uid: int) -> Optional[str]:
    row = db.scalar(select(CaseCounsel).where(CaseCounsel.case_id == case_id, CaseCounsel.user_id == uid))
    return row.side if row else None


def claimant_lead(db: Session, c: Case, user: User) -> bool:
    return teams_of(db, c.id)["claimant"][:1] == [user.id]


def label(c: Case) -> str:
    return c.case_no or f"Filing #{c.id}"


def fmt_date(d) -> str:
    return d.strftime("%d %b %Y") if d else ""


def case_out(c: Case, teams: dict, viewer: User) -> CaseOut:
    mine = viewer.id in teams["claimant"]
    return CaseOut(
        id=c.id, case_no=c.case_no or "", title=c.title, case_type=c.case_type, court=c.court,
        claimant=c.claimant, respondent=c.respondent, status=c.status,
        claimant_team=teams["claimant"], respondent_team=teams["respondent"],
        fee_amount=c.fee_amount, fee_receipt=c.fee_receipt,
        judge_id=c.judge_id or 0, courtroom=c.courtroom,
        case_code=(c.case_code or "") if (viewer.role == "registry" or mine) else "",
        response_due=fmt_date(c.response_due),
        defects=[d for d in c.defects.split("\n") if d],
        registry_note=c.registry_note if viewer.role == "registry" else "",
        filed_at=millis(c.filed_at) if c.filed_at else 0,
    )


def one_out(db: Session, c: Case, viewer: User) -> CaseOut:
    return case_out(c, teams_of(db, c.id), viewer)


def doc_out(d: Document) -> DocOut:
    return DocOut(
        id=d.id, case_id=d.case_id, name=d.name, doc_type=d.doc_type, side=d.side,
        filed=d.filed, uploaded_by=d.uploaded_by, pages=d.pages, created_at=millis(d.created_at),
    )


def order_out(o: Order) -> OrderOut:
    return OrderOut(
        id=o.id, case_id=o.case_id, judge_id=o.judge_id, kind=o.kind,
        title=o.title, body=o.body, created_at=millis(o.created_at),
    )


def note_out(n: PrivateNote) -> NoteOut:
    return NoteOut(id=n.id, case_id=n.case_id, user_id=n.user_id, text=n.text, created_at=millis(n.created_at))


def tell(db: Session, ids, title: str, body: str, case_id: int) -> None:
    for uid in dict.fromkeys(i for i in ids if i):
        notify(db, uid, title, body, case_id)


def registry_ids(db: Session) -> list:
    return list(db.scalars(select(User.id).where(User.role == "registry", User.status == "approved")))


def everyone(teams: dict) -> list:
    return teams["claimant"] + teams["respondent"]


def scrutiny(db: Session, c: Case) -> list:
    docs = list(db.scalars(select(Document).where(Document.case_id == c.id, Document.side == "claimant")))
    return [
        ("Case details complete", bool(c.title.strip() and c.case_type.strip() and c.court.strip())),
        ("Both parties named", bool(c.claimant.strip() and c.respondent.strip())),
        ("Plaint uploaded", any(d.doc_type == "Plaint" for d in docs)),
        ("Vakalatnama uploaded", any(d.doc_type == "Vakalatnama" for d in docs)),
        ("Court fee entered", bool(c.fee_amount.strip() and c.fee_receipt.strip())),
    ]


def open_cases(db: Session, judge_id: int) -> int:
    return db.scalar(
        select(func.count()).select_from(Case).where(Case.judge_id == judge_id, Case.status != "closed")
    ) or 0


def suggest_judge(db: Session, court: str) -> Optional[User]:
    judges = list(db.scalars(
        select(User).where(User.role == "judge", User.status == "approved", User.court == court)
    ))
    return min(judges, key=lambda j: open_cases(db, j.id), default=None)


def next_case_no(db: Session) -> str:
    last = 0
    for (no,) in db.execute(select(Case.case_no).where(Case.case_no.is_not(None))):
        m = re.match(r"OS (\d+)/", no)
        if m:
            last = max(last, int(m.group(1)))
    return f"OS {last + 1}/{datetime.now().year}"


def new_code(db: Session) -> str:
    chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    while True:
        code = "CE-" + "".join(secrets.choice(chars) for _ in range(5))
        if not db.scalar(select(Case.id).where(Case.case_code == code)):
            return code


def overdue(c: Case, teams: dict) -> bool:
    return bool(c.status == "notice" and not teams["respondent"] and c.response_due and c.response_due < date.today())


# ---------- what each person can see ----------

@app.get("/sync/cases", response_model=SyncOut)
def sync_cases(user: User = Depends(approved_user), db: Session = Depends(get_db)):
    if user.role == "registry":
        cases = list(db.scalars(select(Case).where(Case.status != "draft").order_by(Case.id.desc())))
    elif user.role == "judge":
        cases = list(db.scalars(
            select(Case).where(Case.judge_id == user.id, Case.status.in_(JUDGE_SEES)).order_by(Case.id.desc())
        ))
    else:
        mine = select(CaseCounsel.case_id).where(CaseCounsel.user_id == user.id)
        cases = list(db.scalars(select(Case).where(Case.id.in_(mine)).order_by(Case.id.desc())))

    ids = [c.id for c in cases]
    teams = counsel_map(db, ids)
    docs, orders, notes = [], [], []
    if ids:
        for d in db.scalars(select(Document).where(Document.case_id.in_(ids)).order_by(Document.id)):
            t = teams[d.case_id]
            my_side = "claimant" if user.id in t["claimant"] else "respondent" if user.id in t["respondent"] else None
            if d.filed or (user.role == "lawyer" and d.side == my_side):
                docs.append(doc_out(d))
        orders = [order_out(o) for o in db.scalars(select(Order).where(Order.case_id.in_(ids)).order_by(Order.id))]
        notes = [
            note_out(n) for n in db.scalars(
                select(PrivateNote).where(PrivateNote.case_id.in_(ids), PrivateNote.user_id == user.id)
            )
        ]
    return SyncOut(cases=[case_out(c, teams[c.id], user) for c in cases], docs=docs, orders=orders, notes=notes)


# ---------- lawyer: draft, edit, file ----------

@app.post("/cases", response_model=CaseOut, status_code=201)
def create_case(body: CaseIn, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = Case(
        title=body.title, case_type=body.case_type, court=body.court, claimant=body.claimant,
        respondent=body.respondent, fee_amount=body.fee_amount, fee_receipt=body.fee_receipt, status="draft",
    )
    db.add(c)
    db.flush()
    db.add(CaseCounsel(case_id=c.id, user_id=user.id, side="claimant", position=0))
    audit(db, user.id, "Created draft", c.title)
    db.commit()
    db.refresh(c)
    return one_out(db, c, user)


@app.put("/cases/{cid}", response_model=CaseOut)
def update_case(cid: int, body: CaseIn, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if not claimant_lead(db, c, user):
        raise HTTPException(status_code=403, detail="Only the lead advocate can edit this case")
    if c.status not in EDITABLE:
        raise HTTPException(status_code=400, detail="This case can no longer be edited")
    c.title, c.case_type, c.court = body.title, body.case_type, body.court
    c.claimant, c.respondent = body.claimant, body.respondent
    c.fee_amount, c.fee_receipt = body.fee_amount, body.fee_receipt
    audit(db, user.id, "Edited case", c.title)
    db.commit()
    db.refresh(c)
    return one_out(db, c, user)


@app.delete("/cases/{cid}", response_model=MessageOut)
def delete_case(cid: int, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if not claimant_lead(db, c, user):
        raise HTTPException(status_code=403, detail="Only the lead advocate can delete this case")
    if c.status != "draft":
        raise HTTPException(status_code=400, detail="Only a draft can be deleted")
    title = c.title
    for table in (Document, CaseCounsel, Order, PrivateNote):
        db.execute(delete(table).where(table.case_id == cid))
    db.delete(c)
    audit(db, user.id, "Deleted draft", title)
    db.commit()
    return MessageOut(message="Deleted")


@app.post("/cases/{cid}/file", response_model=CaseOut)
def file_case(cid: int, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if not claimant_lead(db, c, user):
        raise HTTPException(status_code=403, detail="Only the lead advocate can file a case")
    if c.status not in EDITABLE:
        raise HTTPException(status_code=400, detail="This case has already been filed")
    if not (c.title.strip() and c.claimant.strip() and c.respondent.strip()):
        raise HTTPException(status_code=400, detail="Complete the case details first")
    c.status = "filed"
    c.filed_at = utcnow()
    c.defects = ""
    for d in db.scalars(select(Document).where(Document.case_id == cid, Document.side == "claimant")):
        d.filed = True
    tell(db, registry_ids(db), "New filing", f"{c.title} · filed for scrutiny", c.id)
    audit(db, user.id, "Filed case", c.title)
    db.commit()
    db.refresh(c)
    return one_out(db, c, user)


# ---------- lawyer: respondent appears ----------

def by_code(db: Session, code: str) -> Optional[Case]:
    code = code.strip().upper()
    if not code:
        return None
    return db.scalar(select(Case).where(func.upper(Case.case_code) == code))


@app.get("/cases/by-code/{code}", response_model=PreviewOut)
def preview_by_code(code: str, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = by_code(db, code)
    if c is None:
        raise HTTPException(status_code=404, detail="No case found for this code")
    return PreviewOut(
        id=c.id, case_no=c.case_no or "", title=c.title, case_type=c.case_type, court=c.court,
        claimant=c.claimant, respondent=c.respondent, response_due=fmt_date(c.response_due),
    )


@app.post("/cases/join", response_model=CaseOut)
def join_case(body: JoinIn, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = by_code(db, body.code)
    if c is None:
        raise HTTPException(status_code=404, detail="No case found for this code")
    teams = teams_of(db, c.id)
    if user.id in teams["claimant"]:
        raise HTTPException(status_code=400, detail="You act for the claimant in this case")
    if user.id in teams["respondent"]:
        raise HTTPException(status_code=400, detail="You already appear in this case")
    if teams["respondent"]:
        raise HTTPException(
            status_code=400,
            detail="A respondent's advocate has already appeared. Ask the lead advocate to invite you.",
        )
    if c.status != "notice":
        raise HTTPException(status_code=400, detail="This case is not open for appearance")
    if not body.file_name.strip():
        raise HTTPException(status_code=400, detail="Upload your vakalatnama")
    db.add(CaseCounsel(case_id=c.id, user_id=user.id, side="respondent", position=0))
    db.add(Document(
        case_id=c.id, name=body.file_name.strip()[:255], doc_type="Vakalatnama", side="respondent",
        filed=True, uploaded_by=user.id, pages=max(body.pages, 1),
    ))
    c.status = "pleadings"
    tell(db, teams["claimant"][:1] + ([c.judge_id] if c.judge_id else []) + registry_ids(db),
         "Respondent appeared", f"{user.full_name} appears for the respondent in {label(c)}", c.id)
    audit(db, user.id, "Appeared for respondent", f"{label(c)} · code {c.case_code}")
    db.commit()
    db.refresh(c)
    return one_out(db, c, user)


# ---------- lawyer: documents ----------

@app.post("/cases/{cid}/docs", response_model=DocOut, status_code=201)
def add_doc(cid: int, body: DocIn, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    side = side_of(db, c.id, user.id)
    if side is None:
        raise HTTPException(status_code=404, detail="Case not found")
    if c.status == "closed":
        raise HTTPException(status_code=400, detail="This case is closed")
    if body.doc_type not in DOC_TYPES:
        raise HTTPException(status_code=400, detail="Choose a document type")
    if body.doc_type == "Plaint" and side != "claimant":
        raise HTTPException(status_code=400, detail="Only the claimant files a plaint")
    if body.doc_type == "Written statement" and side != "respondent":
        raise HTTPException(status_code=400, detail="Only the respondent files a written statement")
    d = Document(
        case_id=c.id, name=body.name, doc_type=body.doc_type, side=side,
        filed=False, uploaded_by=user.id, pages=max(body.pages, 1),
    )
    db.add(d)
    audit(db, user.id, "Uploaded document", f"{label(c)} · {body.doc_type} · {body.name}")
    db.commit()
    db.refresh(d)
    return doc_out(d)


@app.delete("/docs/{did}", response_model=MessageOut)
def remove_doc(did: int, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    d = db.get(Document, did)
    if d is None:
        raise HTTPException(status_code=404, detail="Document not found")
    if d.filed:
        raise HTTPException(status_code=400, detail="Filed documents can't be removed")
    if d.uploaded_by != user.id:
        raise HTTPException(status_code=403, detail="Only the uploader can remove this draft")
    audit(db, user.id, "Removed draft document", d.name)
    db.delete(d)
    db.commit()
    return MessageOut(message="Removed")


@app.post("/docs/{did}/file", response_model=DocOut)
def file_doc(did: int, user: User = Depends(lawyer_user), db: Session = Depends(get_db)):
    d = db.get(Document, did)
    if d is None:
        raise HTTPException(status_code=404, detail="Document not found")
    c = get_case(db, d.case_id)
    teams = teams_of(db, c.id)
    if user.id not in teams[d.side]:
        raise HTTPException(status_code=403, detail="This is not your side's document")
    if teams[d.side][:1] != [user.id]:
        raise HTTPException(status_code=403, detail="Only the lead advocate can file to the court")
    if d.filed:
        raise HTTPException(status_code=400, detail="Already filed")
    d.filed = True
    other = "respondent" if d.side == "claimant" else "claimant"
    tell(db, ([c.judge_id] if c.judge_id else []) + teams[other][:1],
         "Document filed", f"{label(c)} · {d.doc_type}: {d.name}", c.id)
    audit(db, user.id, "Filed document", f"{label(c)} · {d.name}")
    db.commit()
    db.refresh(d)
    return doc_out(d)


# ---------- registry: scrutiny, number, allocation, notice ----------

@app.post("/registry/cases/{cid}/return", response_model=CaseOut)
def return_case(cid: int, body: ReturnIn, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if c.status != "filed":
        raise HTTPException(status_code=400, detail="Only filed cases can be returned")
    defects = [f"Needs attention: {name}" for name, ok in scrutiny(db, c) if not ok]
    if body.note.strip():
        defects.append(body.note.strip()[:200])
    if not defects:
        raise HTTPException(status_code=400, detail="Add a note: no checklist item has failed")
    c.status = "returned"
    c.defects = "\n".join(defects)
    teams = teams_of(db, c.id)
    tell(db, teams["claimant"][:1], "Case returned", f"{c.title}: {defects[0]}", c.id)
    audit(db, reg.id, "Returned case", f"{c.title} · {len(defects)} defect(s)")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, reg)


@app.post("/registry/cases/{cid}/register", response_model=CaseOut)
def register_case(cid: int, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if c.status != "filed":
        raise HTTPException(status_code=400, detail="Only filed cases can be registered")
    if any(not ok for _, ok in scrutiny(db, c)):
        raise HTTPException(status_code=400, detail="Scrutiny checks have failed. Return the case instead")
    c.case_no = next_case_no(db)
    c.status = "registered"
    c.defects = ""
    teams = teams_of(db, c.id)
    tell(db, teams["claimant"][:1], "Case registered", f"{c.title} is now {c.case_no}", c.id)
    audit(db, reg.id, "Registered case", f"{c.title} → {c.case_no}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, reg)


@app.post("/registry/cases/{cid}/allocate", response_model=CaseOut)
def allocate_case(cid: int, body: AllocateIn, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if c.status != "registered":
        raise HTTPException(status_code=400, detail="Only registered cases can be allocated")
    judge = db.get(User, body.judge_id)
    if judge is None or judge.role != "judge" or judge.status != "approved":
        raise HTTPException(status_code=400, detail="Choose an approved judge")
    suggested = suggest_judge(db, c.court)
    if suggested is not None and suggested.id != judge.id and not body.reason.strip():
        raise HTTPException(status_code=400, detail="Give a reason for choosing a different judge")
    teams = teams_of(db, c.id)
    c.judge_id = judge.id
    c.court = judge.court
    c.courtroom = judge.courtroom
    c.status = "allocated" if not c.case_code else ("notice" if not teams["respondent"] else "pleadings")
    c.registry_note = f"Allocation note: {body.reason.strip()[:200]}" if body.reason.strip() else ""
    tell(db, [judge.id], "New case allocated", f"{label(c)} · {c.title}", c.id)
    tell(db, teams["claimant"][:1] + teams["respondent"][:1], "Case allocated",
         f"{label(c)} is before {judge.full_name}, {judge.courtroom}", c.id)
    audit(db, reg.id, "Allocated case", f"{label(c)} → {judge.full_name}, {judge.court}, {judge.courtroom}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, reg)


@app.post("/registry/cases/{cid}/notice", response_model=CaseOut)
def issue_notice(cid: int, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    if c.status != "allocated":
        raise HTTPException(status_code=400, detail="Allocate the case to a judge first")
    c.case_code = new_code(db)
    c.response_due = date.today() + timedelta(days=30)
    c.status = "notice"
    teams = teams_of(db, c.id)
    due = fmt_date(c.response_due)
    tell(db, teams["claimant"][:1], "Notice issued", f"{label(c)}: case code {c.case_code}, response due {due}", c.id)
    tell(db, [c.judge_id], "Notice issued", f"{label(c)}: response due {due}", c.id)
    audit(db, reg.id, "Issued notice", f"{label(c)} · code {c.case_code} · due {due}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, reg)


@app.post("/registry/cases/{cid}/reissue", response_model=CaseOut)
def reissue_code(cid: int, reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    c = get_case(db, cid)
    teams = teams_of(db, c.id)
    if not c.case_code or teams["respondent"]:
        raise HTTPException(status_code=400, detail="A new code can't be issued now")
    c.case_code = new_code(db)
    audit(db, reg.id, "Reissued case code", f"{label(c)} → {c.case_code}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, reg)


@app.post("/registry/reset-cases", response_model=MessageOut)
def reset_cases(reg: User = Depends(registry_user), db: Session = Depends(get_db)):
    from seed_cases import seed_cases

    for table in (Order, PrivateNote, Document, CaseCounsel, Case):
        db.execute(delete(table))
    db.execute(delete(Notification).where(Notification.case_id != 0))
    db.commit()
    seed_cases(db)
    audit(db, reg.id, "Reset demo cases", "")
    db.commit()
    return MessageOut(message="Reset")


# ---------- judge: review, orders, closing, recusal, private notes ----------

def judge_case(db: Session, cid: int, judge: User) -> Case:
    c = get_case(db, cid)
    if c.judge_id != judge.id:
        raise HTTPException(status_code=404, detail="This case is not allocated to you")
    return c


@app.post("/judge/cases/{cid}/review", response_model=CaseOut)
def start_review(cid: int, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    c = judge_case(db, cid, judge)
    teams = teams_of(db, c.id)
    if c.status != "pleadings" and not overdue(c, teams):
        raise HTTPException(
            status_code=400, detail="Review starts after the respondent appears or the response period ends"
        )
    c.status = "review"
    tell(db, everyone(teams), "Under review", f"{label(c)}: the judge has started the review", c.id)
    audit(db, judge.id, "Started review", label(c))
    db.commit()
    db.refresh(c)
    return case_out(c, teams, judge)


@app.post("/judge/cases/{cid}/orders", response_model=OrderOut, status_code=201)
def issue_order(cid: int, body: OrderIn, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    c = judge_case(db, cid, judge)
    if c.status not in ("notice", "pleadings", "review"):
        raise HTTPException(status_code=400, detail="An order can't be issued at this stage")
    if body.kind == "Judgment" and c.status != "review":
        raise HTTPException(status_code=400, detail="Start the review before issuing a judgment")
    o = Order(case_id=c.id, judge_id=judge.id, kind=body.kind, title=body.title, body=body.body)
    db.add(o)
    if body.kind == "Judgment":
        c.status = "judgment"
    tell(db, everyone(teams_of(db, c.id)), f"{body.kind} issued", f"{label(c)}: {body.title}", c.id)
    audit(db, judge.id, f"Issued {body.kind}", f"{label(c)} · {body.title}")
    db.commit()
    db.refresh(o)
    return order_out(o)


@app.post("/judge/cases/{cid}/close", response_model=CaseOut)
def close_case(cid: int, body: ReasonIn, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    c = judge_case(db, cid, judge)
    if c.status == "closed":
        raise HTTPException(status_code=400, detail="This case is already closed")
    if len(body.reason.strip()) < 5:
        raise HTTPException(status_code=400, detail="Give the reason for closing")
    db.add(Order(case_id=c.id, judge_id=judge.id, kind="Closing order", title="Case closed", body=body.reason))
    c.status = "closed"
    teams = teams_of(db, c.id)
    tell(db, everyone(teams) + registry_ids(db), "Case closed", f"{label(c)}: {body.reason}", c.id)
    audit(db, judge.id, "Closed case", f"{label(c)} · {body.reason}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, judge)


@app.post("/judge/cases/{cid}/recuse", response_model=CaseOut)
def recuse(cid: int, body: ReasonIn, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    c = judge_case(db, cid, judge)
    if c.status not in ("allocated", "notice", "pleadings", "review"):
        raise HTTPException(status_code=400, detail="You can't step aside at this stage")
    if len(body.reason.strip()) < 5:
        raise HTTPException(status_code=400, detail="Give the reason for stepping aside")
    c.judge_id = None
    c.courtroom = ""
    c.status = "registered"
    c.registry_note = f"Recused by {judge.full_name}: {body.reason}"[:255]
    teams = teams_of(db, c.id)
    tell(db, registry_ids(db), "Judge stepped aside", f"{label(c)} needs a new judge", c.id)
    tell(db, everyone(teams), "Judge changed", f"{label(c)} will be allocated to another judge", c.id)
    audit(db, judge.id, "Recused", f"{label(c)} · {body.reason}")
    db.commit()
    db.refresh(c)
    return case_out(c, teams, judge)


@app.post("/judge/cases/{cid}/notes", response_model=NoteOut, status_code=201)
def add_note(cid: int, body: NoteIn, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    c = judge_case(db, cid, judge)
    n = PrivateNote(case_id=c.id, user_id=judge.id, text=body.text)
    db.add(n)
    db.commit()
    db.refresh(n)
    return note_out(n)


@app.delete("/judge/notes/{nid}", response_model=MessageOut)
def delete_note(nid: int, judge: User = Depends(judge_user), db: Session = Depends(get_db)):
    n = db.get(PrivateNote, nid)
    if n is None or n.user_id != judge.id:
        raise HTTPException(status_code=404, detail="Note not found")
    db.delete(n)
    db.commit()
    return MessageOut(message="Deleted")