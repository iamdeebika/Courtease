from datetime import datetime
from typing import List

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import DateTime, Integer, String, event, select
from sqlalchemy.orm import Mapped, Session, mapped_column
from sqlalchemy.orm.attributes import get_history

from database import Base, get_db
from models import Case, CaseCounsel, Order, User, utcnow


class CaseEvent(Base):
    __tablename__ = "case_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(Integer, index=True)
    kind: Mapped[str] = mapped_column(String(20))
    text: Mapped[str] = mapped_column(String(255))
    actor_name: Mapped[str] = mapped_column(String(120), default="")
    actor_role: Mapped[str] = mapped_column(String(12), default="")  # registry, lawyer or judge
    audience: Mapped[str] = mapped_column(String(8), default="all")  # all, or parties (hidden from the judge)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class EventOut(BaseModel):
    kind: str
    text: str
    actor: str
    role: str
    at: int
    recorded: bool


class HistoryOut(BaseModel):
    events: List[EventOut]
    incomplete: bool


# ---------- recording (runs inside the same database transaction as the change) ----------

def _put(conn, case_id, kind, text, actor, role, audience="all"):
    t = CaseEvent.__table__
    conn.execute(t.insert().values(
        case_id=case_id, kind=kind, text=text[:255], actor_name=(actor or "")[:120],
        actor_role=role, audience=audience, created_at=utcnow(),
    ))


def _name(conn, uid):
    if not uid:
        return ""
    u = User.__table__
    row = conn.execute(select(u.c.full_name).where(u.c.id == uid)).first()
    return row[0] if row else ""


def _lead(conn, case_id, side):
    t = CaseCounsel.__table__
    row = conn.execute(
        select(t.c.user_id).where(t.c.case_id == case_id, t.c.side == side).order_by(t.c.position, t.c.id)
    ).first()
    return row[0] if row else None


def _changed(target, attr):
    h = get_history(target, attr)
    if not h.has_changes():
        return None
    new = h.added[0] if h.added else None
    old = h.deleted[0] if h.deleted else None
    return old, new


def _case_updated(mapper, conn, target):
    status = _changed(target, "status")
    judge = _changed(target, "judge_id")
    if status:
        old, new = status
        if new == "filed":
            _put(conn, target.id, "filed", "Filed for scrutiny", _name(conn, _lead(conn, target.id, "claimant")), "lawyer")
        elif new == "returned":
            _put(conn, target.id, "returned", "Returned to the advocate for correction", "Court registry", "registry", "parties")
        elif new == "registered" and old == "filed":
            _put(conn, target.id, "registered", f"Registered as {target.case_no}", "Court registry", "registry")
        elif new == "notice" and old == "allocated":
            due = target.response_due.strftime("%d %b %Y") if target.response_due else ""
            text = "Notice issued" + (f", response due {due}" if due else "")
            _put(conn, target.id, "notice", text, "Court registry", "registry")
        elif new == "review":
            _put(conn, target.id, "review", "The judge started the review", _name(conn, target.judge_id), "judge")
        elif new == "closed":
            _put(conn, target.id, "closed", "Case closed", _name(conn, target.judge_id), "judge")
    if judge:
        old_j, new_j = judge
        if new_j:
            text = "Allocated to " + _name(conn, new_j) + (f", {target.courtroom}" if target.courtroom else "")
            _put(conn, target.id, "allocated", text, "Court registry", "registry")
        elif old_j:
            _put(
                conn, target.id, "recused",
                "The judge stepped aside. The case returns to the registry for a new allocation.",
                _name(conn, old_j), "judge", "parties",
            )


def _counsel_added(mapper, conn, target):
    if target.side == "respondent" and target.position == 0:
        _put(conn, target.case_id, "appeared", "The respondent's advocate appeared",
             _name(conn, target.user_id), "lawyer")


def _order_added(mapper, conn, target):
    if target.kind == "Closing order":
        return
    _put(conn, target.case_id, "order", f"{target.kind} issued: {target.title}",
         _name(conn, target.judge_id), "judge")


def install_history(app, m):
    """Adds the case history route and starts recording events. 'm' is main.py."""
    event.listen(Case, "after_update", _case_updated)
    event.listen(CaseCounsel, "after_insert", _counsel_added)
    event.listen(Order, "after_insert", _order_added)

    @app.get("/cases/{cid}/history", response_model=HistoryOut)
    def history(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = m.get_case(db, cid)
        if user.role == "registry":
            if c.status == "draft":
                raise HTTPException(status_code=404, detail="Case not found")
            audiences = ("all", "parties")
        elif user.role == "lawyer":
            if m.side_of(db, c.id, user.id) is None:
                raise HTTPException(status_code=404, detail="Case not found")
            audiences = ("all", "parties")
        elif user.role == "judge":
            if c.judge_id != user.id or c.status not in m.JUDGE_SEES:
                raise HTTPException(status_code=404, detail="Case not found")
            audiences = ("all",)
        else:
            raise HTTPException(status_code=404, detail="Case not found")

        rows = list(db.scalars(select(CaseEvent).where(CaseEvent.case_id == c.id).order_by(CaseEvent.id)))
        kinds = {r.kind for r in rows}
        teams = m.teams_of(db, c.id)

        events = [
            EventOut(kind=r.kind, text=r.text, actor=r.actor_name, role=r.actor_role,
                     at=m.millis(r.created_at), recorded=True)
            for r in rows if r.audience in audiences
        ]
        if "filed" not in kinds and c.filed_at:
            lead = db.get(User, teams["claimant"][0]) if teams["claimant"] else None
            events.append(EventOut(
                kind="filed", text="Filed for scrutiny", actor=lead.full_name if lead else "",
                role="lawyer", at=m.millis(c.filed_at), recorded=False,
            ))
        events.sort(key=lambda e: e.at)

        incomplete = bool(
            (c.case_no and "registered" not in kinds)
            or (c.judge_id and "allocated" not in kinds)
            or (c.case_code and "notice" not in kinds)
            or (teams["respondent"] and "appeared" not in kinds)
        )
        return HistoryOut(events=events, incomplete=incomplete)