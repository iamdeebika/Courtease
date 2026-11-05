import json
from collections import Counter
from datetime import datetime, timedelta
from typing import Dict, List

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import DateTime, Integer, Text, UniqueConstraint, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from database import Base, get_db
from models import AuditLog, Case, Document, User, utcnow
from reader import DocRead, SUPPORTED

KIND_ORDER = ["amount", "quantity", "date", "denied", "unsupported"]


class AnalysisSeen(Base):
    __tablename__ = "analysis_seen"
    __table_args__ = (UniqueConstraint("case_id", "user_id", name="uq_analysis_seen"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(Integer, index=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    signature: Mapped[str] = mapped_column(Text, default="{}")
    seen_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class KindCount(BaseModel):
    kind: str
    count: int


class SummaryOut(BaseModel):
    scope: str
    docs_analysed: int
    docs_unread: int
    ocr_pages: int
    events: int
    issues: int
    issues_disputed: int
    flags: int
    flags_by_kind: List[KindCount]
    have_plaint: bool
    have_written_statement: bool
    ever_reviewed: bool
    last_reviewed: int
    stale: bool
    changes: List[str]


def endpoint_of(app, path: str):
    """Finds the function behind a GET route, so the summary can reuse the other stages."""
    for r in app.routes:
        if getattr(r, "path", "") == path and "GET" in (getattr(r, "methods", None) or set()):
            return r.endpoint
    raise RuntimeError("Route not found: " + path)


def leaks(my_side, timeline, issues, flags) -> List[str]:
    """Lists every draft that belongs to the other side. A judge has no side, so any draft is a leak."""
    bad: List[str] = []

    def check(where: str, filed: bool, side: str, name: str) -> None:
        if not filed and side != my_side:
            bad.append(f"{where}: unfiled document from the other side ({name})")

    for e in timeline.events:
        check("timeline", e.filed, e.side, e.doc_name)
    for issue in issues.issues:
        for p in issue.passages:
            check("issues", p.filed, p.side, p.doc_name)
    for f in flags.flags:
        for p in list(f.claimant) + list(f.respondent):
            check("flags", p.filed, p.side, p.doc_name)
    return bad


def make_signature(docs, reads) -> Dict[str, str]:
    sig: Dict[str, str] = {}
    for d in docs:
        if not d.stored_name or d.mime not in SUPPORTED or d.doc_type == "Vakalatnama":
            continue
        rec = reads.get(d.id)
        stamp = rec.updated_at.isoformat() if rec is not None and rec.status == "done" else "unread"
        sig[str(d.id)] = f"{int(d.filed)}|{stamp}|{d.name}"
    return sig


def describe_changes(old: Dict[str, str], new: Dict[str, str]) -> List[str]:
    out: List[str] = []
    for key, value in new.items():
        filed, stamp, name = value.split("|", 2)
        if key not in old:
            out.append(f"New document: {name}")
            continue
        o_filed, o_stamp, _ = old[key].split("|", 2)
        if o_filed == "0" and filed == "1":
            out.append(f"Now filed: {name}")
        if o_stamp != stamp:
            out.append(f"Now read: {name}" if o_stamp == "unread" else f"Read again: {name}")
    for key, value in old.items():
        if key not in new:
            out.append(f"Removed: {value.split('|', 2)[2]}")
    return out[:12]


def install_summary(app, m):
    """Adds the summary and review routes. 'm' is main.py, which owns the shared helpers."""

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

    def visible(db: Session, user: User, c: Case):
        rows = db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
        return [d for d in rows if can_see(db, user, c, d)]

    def reads_of(db: Session, docs):
        if not docs:
            return {}
        ids = [d.id for d in docs]
        return {r.document_id: r for r in db.scalars(select(DocRead).where(DocRead.document_id.in_(ids)))}

    @app.get("/cases/{cid}/summary", response_model=SummaryOut)
    def summary(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        docs = visible(db, user, c)
        reads = reads_of(db, docs)
        sig = make_signature(docs, reads)

        tl = endpoint_of(app, "/cases/{cid}/timeline")(cid=cid, user=user, db=db)
        iss = endpoint_of(app, "/cases/{cid}/issues")(cid=cid, user=user, db=db)
        fl = endpoint_of(app, "/cases/{cid}/flags")(cid=cid, user=user, db=db)

        my_side = m.side_of(db, c.id, user.id) if user.role == "lawyer" else None
        if leaks(my_side, tl, iss, fl):
            m.audit(db, user.id, "Analysis privacy check failed", m.label(c))
            db.commit()
            raise HTTPException(status_code=500, detail="A safety check stopped this analysis. Nothing was shown.")

        seen = db.scalar(select(AnalysisSeen).where(
            AnalysisSeen.case_id == c.id, AnalysisSeen.user_id == user.id
        ))
        changes: List[str] = []
        if seen is not None:
            try:
                old = json.loads(seen.signature)
            except ValueError:
                old = {}
            if not isinstance(old, dict):
                old = {}
            changes = describe_changes(old, sig)

        recent = db.scalar(select(AuditLog.id).where(
            AuditLog.user_id == user.id, AuditLog.action == "Opened case analysis",
            AuditLog.detail == m.label(c), AuditLog.created_at >= utcnow() - timedelta(minutes=10),
            ))
        if not recent:
            m.audit(db, user.id, "Opened case analysis", m.label(c))
            db.commit()

        by_kind = Counter(f.kind for f in fl.flags)
        if user.role == "judge":
            scope = "Filed documents only. Drafts never appear in a judge's analysis."
        else:
            scope = ("Your side's documents, including drafts, plus the other side's filed documents. "
                     "Your drafts stay private to your side.")
        return SummaryOut(
            scope=scope,
            docs_analysed=sum(1 for v in sig.values() if v.split("|", 2)[1] != "unread"),
            docs_unread=sum(1 for v in sig.values() if v.split("|", 2)[1] == "unread"),
            ocr_pages=sum(r.pages_ocr for r in reads.values() if r.status == "done"),
            events=len(tl.events),
            issues=len(iss.issues),
            issues_disputed=sum(1 for i in iss.issues if i.status == "disputed"),
            flags=len(fl.flags),
            flags_by_kind=[KindCount(kind=k, count=by_kind[k]) for k in KIND_ORDER if by_kind.get(k)],
            have_plaint=iss.have_plaint,
            have_written_statement=iss.have_written_statement,
            ever_reviewed=seen is not None,
            last_reviewed=m.millis(seen.seen_at) if seen is not None else 0,
            stale=bool(changes),
            changes=changes,
        )

    @app.post("/cases/{cid}/analysis/seen", response_model=m.MessageOut)
    def mark_seen(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        sig = make_signature(visible(db, user, c), reads_of(db, visible(db, user, c)))
        row = db.scalar(select(AnalysisSeen).where(
            AnalysisSeen.case_id == c.id, AnalysisSeen.user_id == user.id
        ))
        if row is None:
            row = AnalysisSeen(case_id=c.id, user_id=user.id)
            db.add(row)
        row.signature = json.dumps(sig)
        row.seen_at = utcnow()
        m.audit(db, user.id, "Reviewed case analysis", m.label(c))
        db.commit()
        return m.MessageOut(message="Marked as reviewed")