import re
from collections import Counter
from typing import Dict, List, Optional

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from issues import _QTY, role_of
from models import Case, Document, User
from reader import DocPage, DocRead, SUPPORTED
from timeline import UnreadOut, _SPLIT, _blocks, amounts_of, find_dates, label_of

_I = re.I
TOPICS = [
    ("notice", "Legal notice", re.compile(r"\bnotice\b|registered post|\backnowledg\w+", _I)),
    ("cause", "Cause of action and limitation", re.compile(r"cause of action|limitation|time-?barred", _I)),
    ("defect", "Defects and rejection",
     re.compile(r"\b(defect\w*|deficien\w*|reject\w*|damag\w*|faulty|substandard|complain\w*)\b", _I)),
    ("agreement", "Agreement and price",
     re.compile(r"\b(agreement|contract|purchase order)\b|total price|price of|per (?:metre|meter|kg|unit|piece)", _I)),
    ("delivery", "Delivery",
     re.compile(r"\b(deliver\w*|challan|dispatch\w*|received|reached|consignment|supplied)\b", _I)),
    ("payment", "Payment and balance",
     re.compile(r"\b(pay\w*|paid|advance|balance|outstanding|unpaid|dues?|owed|refund|invoice)\b|(?:Rs\.?|INR|₹)\s*\d", _I)),
]
TITLES = {key: title for key, title, _rx in TOPICS}
SUPPORT_TOPICS = ["notice", "agreement", "payment", "defect", "delivery"]

_DENY = re.compile(
    r"\b(den(?:y|ies|ied)|disput\w*|never)\b"
    r"|\b(?:did|does|do|has|have|is|are|was|were)\s+not\b"
    r"|\bnot\s+(?:received|delivered|admitted|true|correct|accepted|liable)\b"
    r"|\bno\s+(?:cause of action|notice|liability)\b",
    _I,
)
_UNIT = {
    "metre": "metres", "metres": "metres", "meter": "metres", "meters": "metres",
    "kg": "kg", "kgs": "kg", "kilogram": "kg", "kilograms": "kg",
    "tonne": "tonnes", "tonnes": "tonnes", "ton": "tonnes", "tons": "tonnes",
    "unit": "units", "units": "units", "pieces": "pieces", "bags": "bags", "acres": "acres",
    "sqft": "sq ft", "squarefeet": "sq ft",
}
_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
_KIND_ORDER = {"amount": 0, "quantity": 1, "date": 2, "denied": 3, "unsupported": 4}

SUMMARY = {
    "amount": "The two sides state different money amounts about this. Neither figure has been checked here.",
    "quantity": "The two sides state different quantities about this. Neither figure has been checked here.",
    "date": "The two sides give different dates for this. Neither date has been checked here.",
    "denied": "The plaint states something here and the written statement contains a denial. "
              "This is a point to verify, not a finding.",
    "unsupported": "The pleadings make statements about this, but none of the other documents you can see "
                   "covers it. The document may simply not have been uploaded.",
}


class PassageFlagOut(BaseModel):
    doc_id: int
    doc_name: str
    doc_type: str
    side: str
    filed: bool
    page: int
    method: str
    role: str
    sentence: str


class FlagOut(BaseModel):
    key: str
    kind: str
    topic: str
    title: str
    summary: str
    claimant_values: List[str]
    respondent_values: List[str]
    claimant: List[PassageFlagOut]
    respondent: List[PassageFlagOut]


class FlagsOut(BaseModel):
    flags: List[FlagOut]
    docs_used: int
    unread: List[UnreadOut]
    have_plaint: bool
    have_written_statement: bool
    have_evidence: bool


def nice(iso: str) -> str:
    try:
        y, mo, d = iso.split("-")
        return f"{int(d)} {_MONTHS[int(mo) - 1]} {y}"
    except Exception:
        return iso


def topic_of(sentence: str) -> Optional[str]:
    for key, _title, rx in TOPICS:
        if rx.search(sentence):
            return key
    return None


def values_of(sentence: str, skip_dates: bool) -> list:
    out = []
    seen = set()

    def add(key, shown):
        if key not in seen:
            seen.add(key)
            out.append((key, shown))

    for disp in amounts_of(sentence):
        try:
            num = float(disp.split(" ", 1)[1].replace(",", ""))
        except (IndexError, ValueError):
            continue
        add(("amount", num, ""), disp)
    for mt in _QTY.finditer(sentence):
        try:
            num = float(mt.group(1).replace(",", ""))
        except ValueError:
            continue
        unit = _UNIT.get(re.sub(r"[\s.]", "", mt.group(2).lower()), mt.group(2).lower())
        add(("quantity", num, unit), f"{mt.group(1).rstrip(',')} {unit}")
    if not skip_dates:
        for iso, _shown in find_dates(sentence):
            add(("date", iso, ""), nice(iso))
    return out


def _asserted(recs: List[dict], other_all: set) -> set:
    out = set()
    for r in recs:
        for key, _shown in r["values"]:
            # A figure mentioned only to deny it is the other side's figure, not this side's.
            if r["denial"] and key in other_all:
                continue
            out.add(key)
    return out


def _out(r: dict) -> PassageFlagOut:
    s = r["sentence"]
    return PassageFlagOut(
        doc_id=r["doc_id"], doc_name=r["doc_name"], doc_type=r["doc_type"], side=r["side"],
        filed=r["filed"], page=r["page"], method=r["method"], role=r["role"],
        sentence=s if len(s) <= 320 else s[:317] + "...",
    )


def _pick(recs: List[dict], limit: int = 3) -> List[PassageFlagOut]:
    ordered = sorted(recs, key=lambda r: (0 if r["values"] else 1, r["doc_id"], r["page"]))
    return [_out(r) for r in ordered[:limit]]


def install_flags(app, m):
    """Adds the verification flags route. 'm' is main.py, which owns the shared helpers."""

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

    @app.get("/cases/{cid}/flags", response_model=FlagsOut)
    def flags(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        docs = [
            d for d in db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
            if can_see(db, user, c, d)
        ]
        reads = {}
        if docs:
            ids = [d.id for d in docs]
            reads = {r.document_id: r for r in db.scalars(select(DocRead).where(DocRead.document_id.in_(ids)))}

        recs: List[dict] = []
        unread: List[UnreadOut] = []
        used = 0
        have_plaint = have_ws = have_evidence = False
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
            if d.doc_type == "Vakalatnama":
                continue
            pages = list(db.scalars(select(DocPage).where(DocPage.document_id == d.id).order_by(DocPage.page_no)))
            if not any(p.text.strip() for p in pages):
                continue
            used += 1
            role = role_of(d)
            pleading = role in ("Plaint", "Written statement")
            if role == "Plaint":
                have_plaint = True
            elif role == "Written statement":
                have_ws = True
            else:
                have_evidence = True
            for p in pages:
                if not p.text.strip():
                    continue
                sentences = []
                for block in _blocks(p.text):
                    for raw in _SPLIT.split(block):
                        s = raw.replace("\x01", ".").strip()
                        if len(s) >= 8:
                            sentences.append((s, topic_of(s)))
                counts = Counter(t for _s, t in sentences if t)
                dominant = counts.most_common(1)[0][0] if counts else None
                for s, t in sentences:
                    skip_dates = pleading and label_of(s) == "Document dated"
                    vals = values_of(s, skip_dates)
                    topic = t or (dominant if vals else None)
                    if topic is None:
                        continue
                    recs.append({
                        "topic": topic, "side": d.side, "role": role, "pleading": pleading,
                        "doc_id": d.id, "doc_name": d.name, "doc_type": d.doc_type, "filed": d.filed,
                        "page": p.page_no, "method": p.method, "sentence": s,
                        "denial": bool(_DENY.search(s)), "values": vals,
                    })

        c_recs = [r for r in recs if r["side"] == "claimant"]
        r_recs = [r for r in recs if r["side"] == "respondent"]
        c_all = {k for r in c_recs for k, _s in r["values"]}
        r_all = {k for r in r_recs for k, _s in r["values"]}
        c_only = _asserted(c_recs, r_all) - _asserted(r_recs, c_all)
        r_only = _asserted(r_recs, c_all) - _asserted(c_recs, r_all)

        out: List[FlagOut] = []

        def differ(kind: str, unit: str, topic: str):
            def select_side(side_recs, only):
                rows, shown = [], []
                for r in side_recs:
                    if r["topic"] != topic:
                        continue
                    hit = [
                        (k, label) for k, label in r["values"]
                        if k in only and k[0] == kind and (kind != "quantity" or k[2] == unit)
                    ]
                    if hit:
                        rows.append(r)
                        for _k, label in hit:
                            if label not in shown:
                                shown.append(label)
                return rows, shown

            crows, cvals = select_side(c_recs, c_only)
            rrows, rvals = select_side(r_recs, r_only)
            if not (crows and rrows):
                return
            word = {"amount": "Money amounts differ", "quantity": f"Quantities differ ({unit})",
                    "date": "Dates differ"}[kind]
            out.append(FlagOut(
                key=f"{kind}-{topic}-{unit}", kind=kind, topic=topic,
                title=f"{word}: {TITLES[topic]}", summary=SUMMARY[kind],
                claimant_values=cvals[:6], respondent_values=rvals[:6],
                claimant=_pick(crows), respondent=_pick(rrows),
            ))

        units = sorted({k[2] for k in (c_only | r_only) if k[0] == "quantity"})
        for key, _title, _rx in TOPICS:
            differ("amount", "", key)
            for unit in units:
                differ("quantity", unit, key)
            differ("date", "", key)

        for key, _title, _rx in TOPICS:
            denials = [r for r in r_recs if r["role"] == "Written statement" and r["denial"] and r["topic"] == key]
            claims = [r for r in c_recs if r["role"] == "Plaint" and r["topic"] == key]
            if denials and claims:
                out.append(FlagOut(
                    key=f"denied-{key}", kind="denied", topic=key,
                    title=f"Denied: {TITLES[key]}", summary=SUMMARY["denied"],
                    claimant_values=[], respondent_values=[],
                    claimant=_pick(claims, 2), respondent=_pick(denials, 2),
                ))

        for key in SUPPORT_TOPICS:
            pleaded = [r for r in recs if r["pleading"] and r["topic"] == key]
            backed = [r for r in recs if not r["pleading"] and r["topic"] == key]
            if pleaded and not backed:
                out.append(FlagOut(
                    key=f"unsupported-{key}", kind="unsupported", topic=key,
                    title=f"No supporting document: {TITLES[key]}", summary=SUMMARY["unsupported"],
                    claimant_values=[], respondent_values=[],
                    claimant=_pick([r for r in pleaded if r["side"] == "claimant"], 2),
                    respondent=_pick([r for r in pleaded if r["side"] == "respondent"], 2),
                ))

        out.sort(key=lambda f: _KIND_ORDER[f.kind])
        return FlagsOut(
            flags=out, docs_used=used, unread=unread, have_plaint=have_plaint,
            have_written_statement=have_ws, have_evidence=have_evidence,
        )