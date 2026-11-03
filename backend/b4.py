import threading
import time
from datetime import date, timedelta
from typing import List

from fastapi import Depends
from pydantic import BaseModel
from sqlalchemy import Integer, String, UniqueConstraint, func, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from database import Base, SessionLocal, get_db
from models import Case, Document, Order, TeamTask, User, utcnow

ORDER = ["filed", "returned", "registered", "allocated", "notice", "pleadings", "review", "judgment", "closed"]


class ReminderSent(Base):
    """Remembers which reminders were already sent, so nobody gets the same one twice."""
    __tablename__ = "reminders_sent"
    __table_args__ = (UniqueConstraint("kind", "ref_id", "user_id", name="uq_reminder"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    kind: Mapped[str] = mapped_column(String(20))
    ref_id: Mapped[int] = mapped_column(Integer)
    user_id: Mapped[int] = mapped_column(Integer)


class StatusCount(BaseModel):
    status: str
    count: int


class JudgeLoad(BaseModel):
    name: str
    court: str
    courtroom: str
    open_cases: int
    closed_cases: int


class ReportOut(BaseModel):
    pending_accounts: int
    approved_judges: int
    approved_lawyers: int
    suspended_accounts: int
    total_cases: int
    filed_last_7_days: int
    awaiting_scrutiny: int
    awaiting_allocation: int
    awaiting_notice: int
    overdue_responses: int
    documents_filed: int
    orders_issued: int
    by_status: List[StatusCount]
    judges: List[JudgeLoad]


def run_reminders(m) -> int:
    """Creates alerts for overdue responses and for tasks that are due. Returns how many were created."""
    created = 0
    db = SessionLocal()
    try:
        today = date.today()

        def once(kind: str, ref: int, uid: int, title: str, body: str, case_id: int) -> None:
            nonlocal created
            seen = db.scalar(select(ReminderSent.id).where(
                ReminderSent.kind == kind, ReminderSent.ref_id == ref, ReminderSent.user_id == uid
            ))
            if seen:
                return
            db.add(ReminderSent(kind=kind, ref_id=ref, user_id=uid))
            m.notify(db, uid, title, body, case_id)
            created += 1

        late_cases = db.scalars(select(Case).where(
            Case.status == "notice", Case.response_due.is_not(None), Case.response_due < today
        ))
        for c in late_cases:
            teams = m.teams_of(db, c.id)
            if teams["respondent"]:
                continue
            targets = m.registry_ids(db) + ([c.judge_id] if c.judge_id else []) + teams["claimant"][:1]
            for uid in dict.fromkeys(targets):
                once(
                    "overdue", c.id, uid, "Response overdue",
                    f"{m.label(c)}: no appearance by {m.fmt_date(c.response_due)}", c.id,
                )

        tasks = db.scalars(select(TeamTask).where(
            TeamTask.done.is_(False), TeamTask.due <= today + timedelta(days=1)
        ))
        for t in tasks:
            late = t.due < today
            once(
                "task_late" if late else "task_soon", t.id, t.assignee_id,
                "Task overdue" if late else "Task due soon",
                f"{t.title} · due {m.fmt_date(t.due)}", t.case_id,
            )

        db.commit()
    finally:
        db.close()
    return created


def start_scheduler(m) -> None:
    def loop() -> None:
        time.sleep(5)
        while True:
            try:
                run_reminders(m)
            except Exception as exc:  # keep the server alive whatever happens
                print("Reminder run failed:", exc)
            time.sleep(1800)

    threading.Thread(target=loop, daemon=True).start()


def install_b4(app, m) -> None:
    """Adds the report and reminder routes. 'm' is main.py, which owns the shared helpers."""

    @app.get("/registry/report", response_model=ReportOut)
    def report(reg: User = Depends(m.registry_user), db: Session = Depends(get_db)):
        def count(stmt) -> int:
            return db.scalar(stmt) or 0

        def users(role: str, status: str) -> int:
            return count(select(func.count()).select_from(User).where(User.role == role, User.status == status))

        cases = list(db.scalars(select(Case).where(Case.status != "draft")))
        by_status = {s: 0 for s in ORDER}
        overdue = 0
        today = date.today()
        for c in cases:
            by_status[c.status] = by_status.get(c.status, 0) + 1
            if c.status == "notice" and c.response_due and c.response_due < today:
                if not m.teams_of(db, c.id)["respondent"]:
                    overdue += 1

        week_ago = utcnow() - timedelta(days=7)
        judges = []
        for j in db.scalars(select(User).where(User.role == "judge", User.status == "approved").order_by(User.id)):
            open_n = count(select(func.count()).select_from(Case).where(Case.judge_id == j.id, Case.status != "closed"))
            closed_n = count(select(func.count()).select_from(Case).where(Case.judge_id == j.id, Case.status == "closed"))
            judges.append(JudgeLoad(
                name=j.full_name, court=j.court, courtroom=j.courtroom,
                open_cases=open_n, closed_cases=closed_n,
            ))

        return ReportOut(
            pending_accounts=count(select(func.count()).select_from(User).where(User.status == "pending")),
            approved_judges=users("judge", "approved"),
            approved_lawyers=users("lawyer", "approved"),
            suspended_accounts=count(select(func.count()).select_from(User).where(User.status == "suspended")),
            total_cases=len(cases),
            filed_last_7_days=count(select(func.count()).select_from(Case).where(
                Case.filed_at.is_not(None), Case.filed_at >= week_ago
            )),
            awaiting_scrutiny=by_status.get("filed", 0),
            awaiting_allocation=by_status.get("registered", 0),
            awaiting_notice=by_status.get("allocated", 0),
            overdue_responses=overdue,
            documents_filed=count(select(func.count()).select_from(Document).where(Document.filed.is_(True))),
            orders_issued=count(select(func.count()).select_from(Order)),
            by_status=[StatusCount(status=s, count=by_status[s]) for s in ORDER if by_status.get(s, 0) > 0],
            judges=judges,
        )

    @app.post("/registry/run-reminders", response_model=m.MessageOut)
    def reminders_now(reg: User = Depends(m.registry_user)):
        n = run_reminders(m)
        return m.MessageOut(message=f"Created {n} reminder(s)")

    start_scheduler(m)