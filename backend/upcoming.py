from datetime import date
from typing import List

from fastapi import Depends
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from models import Case, CaseCounsel, TeamTask, User

MAX_ITEMS = 50
RANK = {"response": 0, "task": 1}


class UpcomingItem(BaseModel):
    kind: str
    title: str
    detail: str
    case_id: int
    case_label: str
    date_iso: str
    date_text: str
    days: int
    due_text: str


class UpcomingOut(BaseModel):
    items: List[UpcomingItem]
    overdue: int
    total: int


def due_text(days: int) -> str:
    if days < 0:
        n = -days
        return f"Overdue by {n} day" + ("" if n == 1 else "s")
    if days == 0:
        return "Due today"
    if days == 1:
        return "Due tomorrow"
    return f"Due in {days} days"


def install_upcoming(app, m):
    """Adds the upcoming deadlines route. 'm' is main.py, which owns the shared helpers."""

    @app.get("/upcoming", response_model=UpcomingOut)
    def upcoming(user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        today = date.today()
        items: List[UpcomingItem] = []

        def add(kind, title, detail, c, d):
            days = (d - today).days
            items.append(UpcomingItem(
                kind=kind, title=title, detail=detail, case_id=c.id, case_label=m.label(c),
                date_iso=d.isoformat(), date_text=d.strftime("%d %b %Y"), days=days, due_text=due_text(days),
            ))

        q = select(Case).where(Case.status == "notice", Case.response_due.is_not(None))
        if user.role == "judge":
            q = q.where(Case.judge_id == user.id)
        elif user.role == "lawyer":
            mine = select(CaseCounsel.case_id).where(CaseCounsel.user_id == user.id, CaseCounsel.side == "claimant")
            q = q.where(Case.id.in_(mine))
        cases = list(db.scalars(q))
        teams = m.counsel_map(db, [c.id for c in cases])
        for c in cases:
            if teams[c.id]["respondent"]:
                continue
            add("response", "Response due from the respondent", f"{m.label(c)} · {c.title}", c, c.response_due)

        if user.role == "lawyer":
            for t in db.scalars(select(TeamTask).where(TeamTask.assignee_id == user.id, TeamTask.done.is_(False))):
                if m.side_of(db, t.case_id, user.id) != t.side:
                    continue
                c = db.get(Case, t.case_id)
                if c is None:
                    continue
                add("task", t.title, f"Task · {m.label(c)} · {c.title}", c, t.due)

        items.sort(key=lambda i: (i.days, RANK[i.kind]))
        return UpcomingOut(
            items=items[:MAX_ITEMS], overdue=sum(1 for i in items if i.days < 0), total=len(items),
        )