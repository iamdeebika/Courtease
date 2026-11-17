from datetime import datetime
from typing import List

from fastapi import Depends
from pydantic import BaseModel
from sqlalchemy import Boolean, DateTime, Integer, Text, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from database import Base, get_db
from models import User, utcnow

ALL = ("lawyer", "judge", "registry")

CATEGORIES = [
    {"key": "account", "label": "Account decisions",
     "detail": "Account requests, approvals, rejections and suspensions", "roles": ALL},
    {"key": "filing", "label": "Case steps",
     "detail": "Returned for correction, registered, allocated, notice issued, review started, closed", "roles": ALL},
    {"key": "respondent", "label": "Respondent appears",
     "detail": "The other side's advocate appeared, or the response date passed", "roles": ALL},
    {"key": "orders", "label": "Orders and judgments",
     "detail": "A direction, order or judgment was issued", "roles": ("lawyer", "judge")},
    {"key": "documents", "label": "Documents",
     "detail": "A document was filed to the court", "roles": ("lawyer", "judge")},
    {"key": "team", "label": "Team and tasks",
     "detail": "Invitations, new members, tasks and messages", "roles": ("lawyer",)},
    {"key": "reminders", "label": "Reminders",
     "detail": "Due dates and overdue items", "roles": ALL},
    {"key": "other", "label": "Everything else",
     "detail": "Any other alert", "roles": ALL},
]

# The first matching rule wins, so the order matters.
RULES = [
    ("account", ("account", "approved", "rejected", "suspended", "reinstated", "sign-up", "signup")),
    ("orders", ("order", "direction", "judgment", "judgement")),
    ("reminders", ("reminder", "overdue", "due ", "deadline")),
    ("team", ("invit", "team", "task", "message", "chat")),
    ("documents", ("document", "uploaded")),
    ("respondent", ("respondent", "appeared", "response")),
    ("filing", ("filed", "returned", "registered", "allocated", "notice", "closed", "review",
                "stepped aside", "scrutiny", "correction", "case")),
]


class PushPrefs(Base):
    __tablename__ = "push_prefs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, unique=True, index=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    muted: Mapped[str] = mapped_column(Text, default="")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class OptionOut(BaseModel):
    key: str
    label: str
    detail: str
    enabled: bool


class SettingsOut(BaseModel):
    enabled: bool
    options: List[OptionOut]


class SettingsIn(BaseModel):
    enabled: bool = True
    muted: List[str] = []


def classify(title: str, body: str) -> str:
    text = ((title or "") + " " + (body or "")).lower()
    for key, words in RULES:
        if any(w in text for w in words):
            return key
    return "other"


def wants(connection, user_id: int, title: str, body: str) -> bool:
    """True when this user should get a push for this alert. No saved row means everything is on."""
    t = PushPrefs.__table__
    row = connection.execute(select(t.c.enabled, t.c.muted).where(t.c.user_id == user_id)).first()
    if row is None:
        return True
    if not row[0]:
        return False
    muted = {k for k in (row[1] or "").split(",") if k}
    return classify(title, body) not in muted


def _build(db: Session, user: User) -> SettingsOut:
    row = db.scalar(select(PushPrefs).where(PushPrefs.user_id == user.id))
    enabled = True if row is None else bool(row.enabled)
    muted = set() if row is None else {k for k in (row.muted or "").split(",") if k}
    options = [
        OptionOut(key=c["key"], label=c["label"], detail=c["detail"], enabled=c["key"] not in muted)
        for c in CATEGORIES if user.role in c["roles"]
    ]
    return SettingsOut(enabled=enabled, options=options)


def install_prefs(app, m):
    """Adds the push settings routes. 'm' is main.py, which owns the shared helpers."""

    @app.get("/push/settings", response_model=SettingsOut)
    def get_settings(user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        return _build(db, user)

    @app.put("/push/settings", response_model=SettingsOut)
    def put_settings(body: SettingsIn, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        valid = {c["key"] for c in CATEGORIES if user.role in c["roles"]}
        muted = sorted({k for k in body.muted if k in valid})
        row = db.scalar(select(PushPrefs).where(PushPrefs.user_id == user.id))
        if row is None:
            row = PushPrefs(user_id=user.id)
            db.add(row)
        row.enabled = body.enabled
        row.muted = ",".join(muted)
        row.updated_at = utcnow()
        db.commit()
        return _build(db, user)
