import re

from fastapi import Depends, HTTPException
from pydantic import BaseModel, field_validator
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from database import get_db
from law import SavedOut, SavedPrecedent
from models import Case, User

SCHEME = re.compile(r"^https?://", re.I)
LOOKS_LIKE_HOST = re.compile(r"^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/\S*)?$")


class ManualIn(BaseModel):
    case_id: int = 0
    title: str
    citation: str = ""
    court: str = ""
    date: str = ""
    summary: str = ""
    source_url: str = ""
    note: str = ""

    @field_validator("case_id")
    @classmethod
    def _case(cls, v: int) -> int:
        return max(v, 0)

    @field_validator("title")
    @classmethod
    def _title(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("Enter the case name")
        if len(v) > 300:
            raise ValueError("The case name is too long")
        return v

    @field_validator("citation")
    @classmethod
    def _citation(cls, v: str) -> str:
        v = v.strip()
        if len(v) > 80:
            raise ValueError("The citation is too long (80 characters at most)")
        return v

    @field_validator("court")
    @classmethod
    def _court(cls, v: str) -> str:
        return v.strip()[:120]

    @field_validator("date")
    @classmethod
    def _date(cls, v: str) -> str:
        return v.strip()[:30]

    @field_validator("summary")
    @classmethod
    def _summary(cls, v: str) -> str:
        v = v.strip()
        if len(v) > 2000:
            raise ValueError("The key point is too long (2000 characters at most)")
        return v

    @field_validator("note")
    @classmethod
    def _note(cls, v: str) -> str:
        v = v.strip()
        if len(v) > 500:
            raise ValueError("The note is too long (500 characters at most)")
        return v

    @field_validator("source_url")
    @classmethod
    def _url(cls, v: str) -> str:
        v = v.strip()
        if not v:
            return ""
        if not SCHEME.match(v) and LOOKS_LIKE_HOST.match(v):
            v = "https://" + v
        if not SCHEME.match(v) or " " in v:
            raise ValueError("The link must start with http:// or https://")
        if len(v) > 600:
            raise ValueError("The link is too long")
        return v


def install_manual(app, m):
    """Adds the route for precedents typed in by the user. 'm' is main.py, which owns the shared helpers."""

    @app.post("/precedents/manual", response_model=SavedOut, status_code=201)
    def save_manual(body: ManualIn, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        if user.role not in ("lawyer", "judge"):
            raise HTTPException(status_code=403, detail="Only advocates and judges can save precedents")

        if body.case_id:
            c = db.get(Case, body.case_id)
            ok = c is not None and (
                    (user.role == "judge" and c.judge_id == user.id)
                    or (user.role == "lawyer" and m.side_of(db, c.id, user.id) is not None)
            )
            if not ok:
                raise HTTPException(status_code=404, detail="Case not found")

        dup = db.scalar(select(SavedPrecedent.id).where(
            SavedPrecedent.user_id == user.id,
            SavedPrecedent.case_id == body.case_id,
            func.lower(SavedPrecedent.title) == body.title.lower(),
            SavedPrecedent.ref == body.citation,
            ))
        if dup:
            raise HTTPException(status_code=400, detail="Already saved for this case")

        p = SavedPrecedent(
            user_id=user.id, case_id=body.case_id, ref=body.citation, title=body.title, court=body.court,
            kind="manual", doc_date=body.date, snippet=body.summary, page=0,
            source_url=body.source_url, page_url="", note=body.note,
        )
        db.add(p)
        m.audit(db, user.id, "Saved precedent (typed in)", body.title)
        db.commit()
        db.refresh(p)
        return SavedOut(
            id=p.id, case_id=p.case_id, ref=p.ref, title=p.title, court=p.court, kind=p.kind,
            date=p.doc_date, snippet=p.snippet, page=p.page, source_url=p.source_url,
            page_url=p.page_url, note=p.note, created_at=m.millis(p.created_at),
        )