import re
from datetime import date
from typing import List

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from models import Case, Document, User
from reader import DocPage, DocRead, SUPPORTED

MAX_EVENTS = 400

MONTHS = {
    "jan": 1, "feb": 2, "mar": 3, "apr": 4, "may": 5, "jun": 6,
    "jul": 7, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12,
}
_MONTH = (
    r"(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?"
    r"|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
)
_DMY = re.compile(
    r"\b(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*(?:of\s+)?" + _MONTH + r"\.?,?[\s\-/.]*(\d{4})\b", re.I
)
_MDY = re.compile(r"\b" + _MONTH + r"\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b", re.I)
_NUM = re.compile(r"\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})\b")
_AMOUNT = re.compile(r"(?:Rs\.?|INR|₹)\s*(\d[\d,]*(?:\.\d+)?)", re.I)
_ABBR = re.compile(r"\b(Rs|No|Nos|Mr|Mrs|Ms|Dr|Smt|Sri|Hon|vs|v|St|Ltd|Pvt|Co)\.", re.I)
_SPLIT = re.compile(r"(?<=[.;!?])\s+(?=[A-Z0-9\"(])")

_RULES = [
    ("Document dated", re.compile(r"^\W*(place|date|dated)\b", re.I)),
    ("Legal notice", re.compile(r"\bnotice\b", re.I)),
    ("Cause of action", re.compile(r"cause of action", re.I)),
    ("Rejection or complaint", re.compile(r"reject|defect|complain", re.I)),
    ("Delivery", re.compile(r"deliver|challan|received|dispatch", re.I)),
    ("Payment", re.compile(r"\badvance\b|\bpaid\b|\bpayment\b|\bpay\b|balance", re.I)),
    ("Agreement", re.compile(r"agreement|contract|agreed to", re.I)),
    ("Filing", re.compile(r"\bfiled\b|\bplaint\b|\bsuit\b", re.I)),
]


class EventOut(BaseModel):
    date_iso: str
    date_text: str
    label: str
    sentence: str
    doc_id: int
    doc_name: str
    doc_type: str
    side: str
    filed: bool
    page: int
    method: str
    amounts: List[str]


class UnreadOut(BaseModel):
    doc_id: int
    name: str
    reason: str


class TimelineOut(BaseModel):
    events: List[EventOut]
    docs_used: int
    unread: List[UnreadOut]


def _make(y: int, mo: int, d: int):
    if not 1950 <= y <= 2100:
        return None
    try:
        return date(y, mo, d)
    except ValueError:
        return None


def find_dates(sentence: str):
    found = []
    for mt in _DMY.finditer(sentence):
        dt = _make(int(mt.group(3)), MONTHS[mt.group(2)[:3].lower()], int(mt.group(1)))
        if dt:
            found.append((mt.start(), dt, mt.group(0)))
    for mt in _MDY.finditer(sentence):
        dt = _make(int(mt.group(3)), MONTHS[mt.group(1)[:3].lower()], int(mt.group(2)))
        if dt:
            found.append((mt.start(), dt, mt.group(0)))
    for mt in _NUM.finditer(sentence):
        dt = _make(int(mt.group(3)), int(mt.group(2)), int(mt.group(1)))
        if dt:
            found.append((mt.start(), dt, mt.group(0)))
    found.sort(key=lambda x: x[0])
    seen = set()
    out = []
    for _, dt, shown in found:
        iso = dt.isoformat()
        if iso in seen:
            continue
        seen.add(iso)
        out.append((iso, shown.strip()))
    return out


def label_of(sentence: str) -> str:
    for name, rx in _RULES:
        if rx.search(sentence):
            return name
    return "Event"


def amounts_of(sentence: str) -> List[str]:
    out: List[str] = []
    for mt in _AMOUNT.finditer(sentence):
        value = "Rs. " + mt.group(1).rstrip(",.")
        if value not in out:
            out.append(value)
    return out[:5]


def _blocks(text: str) -> List[str]:
    """Turns page text into blocks. Wrapped lines are joined, short separate lines (like a form) stay apart."""
    text = text.replace("\r", "").replace("\xa0", " ")
    text = re.sub(r"(?m)^\s*\d{1,2}[.)]\s+", "", text)
    text = _ABBR.sub(lambda m: m.group(1) + "\x01", text)
    out: List[str] = []
    for line in text.split("\n"):
        line = re.sub(r"\s{2,}", " ", line).strip()
        if not line:
            continue
        if (
                out
                and not re.search(r"[.:;!?]$", out[-1])
                and (re.match(r"[a-z0-9(\"']", line) or len(out[-1]) >= 60)
        ):
            out[-1] += " " + line
        else:
            out.append(line)
    return out


def install_timeline(app, m):
    """Adds the timeline route. 'm' is main.py, which owns the shared helpers."""

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

    @app.get("/cases/{cid}/timeline", response_model=TimelineOut)
    def timeline(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        docs = [
            d for d in db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
            if can_see(db, user, c, d)
        ]
        reads = {}
        if docs:
            ids = [d.id for d in docs]
            reads = {r.document_id: r for r in db.scalars(select(DocRead).where(DocRead.document_id.in_(ids)))}

        events: List[EventOut] = []
        seen = set()
        unread: List[UnreadOut] = []
        used = 0
        for d in docs:
            if not d.stored_name or d.mime not in SUPPORTED:
                continue
            rec = reads.get(d.id)
            if rec is None or rec.status != "done":
                if rec is not None and rec.status == "reading":
                    reason = "Reading..."
                elif rec is not None and rec.status == "failed":
                    reason = "Could not be read"
                else:
                    reason = "Not read yet"
                unread.append(UnreadOut(doc_id=d.id, name=d.name, reason=reason))
                continue
            pages = list(db.scalars(select(DocPage).where(DocPage.document_id == d.id).order_by(DocPage.page_no)))
            if any(p.text.strip() for p in pages):
                used += 1
            for p in pages:
                if not p.text.strip():
                    continue
                for block in _blocks(p.text):
                    for raw in _SPLIT.split(block):
                        s = raw.replace("\x01", ".").strip()
                        if len(s) < 8:
                            continue
                        dates = find_dates(s)
                        if not dates:
                            continue
                        shown = s if len(s) <= 320 else s[:317] + "..."
                        label = label_of(s)
                        amounts = amounts_of(s)
                        for iso, text in dates:
                            key = (d.id, p.page_no, iso, s[:80])
                            if key in seen:
                                continue
                            seen.add(key)
                            events.append(EventOut(
                                date_iso=iso, date_text=text, label=label, sentence=shown,
                                doc_id=d.id, doc_name=d.name, doc_type=d.doc_type, side=d.side,
                                filed=d.filed, page=p.page_no, method=p.method, amounts=amounts,
                            ))
        events.sort(key=lambda e: (e.date_iso, e.doc_id, e.page))
        return TimelineOut(events=events[:MAX_EVENTS], docs_used=used, unread=unread)