import re
from typing import Dict, List

from fastapi import Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from models import Case, Document, User
from reader import DocPage, DocRead, SUPPORTED
from timeline import UnreadOut, _SPLIT, _blocks, amounts_of

MAX_PER_ISSUE = 14
MAX_PER_DOC = 3
_I = re.I

# key, title, plain-words description, keyword rule
ISSUES = [
    ("payment", "Payment and balance due",
     "Whether money is due, how much was paid, and what remains.",
     re.compile(r"\b(pay(?:ment|ments|able|ing)?|paid|advance|balance|outstanding|unpaid|dues?|owed|invoice|refund|instal+ments?)\b|(?:Rs\.?|INR|₹)\s*\d", _I)),
    ("delivery", "Delivery of goods or services",
     "Whether, when and how much was delivered, and who received it.",
     re.compile(r"\b(deliver\w*|challan|dispatch\w*|supplied|supply|consignment|received)\b", _I)),
    ("quantity", "Quantity",
     "How much was agreed, supplied or accepted.",
     re.compile(r"\b(quantity|quantities|short\s*supply|shortfall)\b|\d[\d,]*\s*(?:metres?|meters?|kgs?|kilograms?|tonnes?|tons?|units?|pieces|bags|acres|sq\.?\s?ft|square feet)\b", _I)),
    ("delay", "Delay and time of performance",
     "Whether work or delivery was on time under the agreement.",
     re.compile(r"\b(delay\w*|late|belated|on or before|time is of the essence|deadline|stipulated)\b|within\s+\d+\s+days|\d+\s+days?\s+(?:after|before|late)", _I)),
    ("defect", "Defects and quality",
     "Whether goods or work were defective, and whether they were rejected.",
     re.compile(r"\b(defect\w*|deficien\w*|quality|rejected?|rejection|damag\w*|substandard|faulty)\b|good condition", _I)),
    ("notice", "Legal notice",
     "Whether a notice was sent, received and answered.",
     re.compile(r"\bnotice\b|\bregistered post\b|\backnowledg\w+", _I)),
    ("agreement", "The agreement and its terms",
     "What was agreed, and whether the agreement is admitted.",
     re.compile(r"\b(agreement|contract|purchase order|memorandum|MOU|terms and conditions|agreed to|agreed price|agreed rate)\b", _I)),
    ("interest", "Interest and damages",
     "Whether interest, damages or compensation can be claimed.",
     re.compile(r"\b(interest|per annum|damages|compensation|penalty)\b", _I)),
    ("limitation", "Cause of action and limitation",
     "Whether the claim is in time and a cause of action arose.",
     re.compile(r"\b(limitation|cause of action|time-?barred|barred by|maintainab\w*)\b", _I)),
    ("jurisdiction", "Jurisdiction",
     "Whether this court can hear the case.",
     re.compile(r"\b(jurisdiction|territorial|pecuniary|venue)\b", _I)),
    ("possession", "Possession and title",
     "Who owns or occupies the property.",
     re.compile(r"\b(possession|title|ownership|encroach\w*|trespass|boundary|survey number|patta|sale deed|mutation)\b", _I)),
    ("tenancy", "Rent and tenancy",
     "Rent, arrears, lease terms and eviction.",
     re.compile(r"\b(rent|rental|tenan\w+|lease|lessor|lessee|landlord|eviction|arrears|vacate)\b", _I)),
    ("injunction", "Injunction and interim relief",
     "Whether the court should restrain something while the case is pending.",
     re.compile(r"\b(injunction|restrain\w*|status quo|interim relief)\b", _I)),
    ("partition", "Partition and inheritance",
     "How property is shared among family members or heirs.",
     re.compile(r"\b(partition|coparcener\w*|inherit\w*|legal heirs?|succession|ancestral|joint family)\b", _I)),
]

_DENIAL = re.compile(
    r"\b(den(?:y|ies|ied)|disput\w*|never|only|false|incorrect|untrue|contrary)\b"
    r"|\b(?:did|does|do|has|have|is|are|was|were)\s+not\b"
    r"|\bno\s+(?:cause|notice|liability|delivery)\b"
    r"|\bnot\s+(?:received|delivered|paid|liable|admitted|true|correct|accepted)\b",
    _I,
)
_QTY = re.compile(
    r"(\d[\d,]*(?:\.\d+)?)\s*(metres?|meters?|kgs?|kilograms?|tonnes?|tons?|units?|pieces|bags|acres|sq\.?\s?ft|square feet)\b",
    _I,
)
_ORDER = {"disputed": 0, "both": 1, "claimant_only": 2, "respondent_only": 2, "evidence_only": 3}


class PassageOut(BaseModel):
    doc_id: int
    doc_name: str
    doc_type: str
    side: str
    filed: bool
    page: int
    method: str
    role: str
    sentence: str
    figures: List[str]
    denial: bool


class IssueOut(BaseModel):
    key: str
    title: str
    plain: str
    status: str
    status_text: str
    found: int
    passages: List[PassageOut]


class IssuesOut(BaseModel):
    issues: List[IssueOut]
    docs_used: int
    unread: List[UnreadOut]
    have_plaint: bool
    have_written_statement: bool


def figures_of(sentence: str) -> List[str]:
    out = amounts_of(sentence)
    for mt in _QTY.finditer(sentence):
        value = f"{mt.group(1).rstrip(',')} {mt.group(2).lower()}"
        if value not in out:
            out.append(value)
    return out[:6]


def role_of(d: Document) -> str:
    if d.doc_type == "Plaint":
        return "Plaint"
    if d.doc_type == "Written statement":
        return "Written statement"
    return "Claimant's document" if d.side == "claimant" else "Respondent's document"


def status_of(items: List[PassageOut], have_plaint: bool, have_ws: bool):
    claimant = [p for p in items if p.role == "Plaint"]
    respondent = [p for p in items if p.role == "Written statement"]
    if claimant and respondent:
        if any(p.denial for p in respondent):
            return "disputed", (
                "Both the plaint and the written statement address this, and the written statement "
                "contains a denial or a different account. This appears to be in dispute."
            )
        return "both", (
            "Both the plaint and the written statement mention this. No denial was found by the rules, "
            "so check whether the two sides agree."
        )
    if claimant:
        return "claimant_only", "Raised in the plaint." + (
            " The written statement does not mention it." if have_ws else " No written statement has been read yet."
        )
    if respondent:
        return "respondent_only", "Raised in the written statement." + (
            " The plaint does not mention it." if have_plaint else " No plaint has been read yet."
        )
    return "evidence_only", "Mentioned only in other documents. Neither pleading was found to mention it."


def install_issues(app, m):
    """Adds the issues and evidence route. 'm' is main.py, which owns the shared helpers."""

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

    @app.get("/cases/{cid}/issues", response_model=IssuesOut)
    def issues(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        docs = [
            d for d in db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
            if can_see(db, user, c, d)
        ]
        reads = {}
        if docs:
            ids = [d.id for d in docs]
            reads = {r.document_id: r for r in db.scalars(select(DocRead).where(DocRead.document_id.in_(ids)))}

        buckets: Dict[str, List[tuple]] = {key: [] for key, _t, _p, _r in ISSUES}
        unread: List[UnreadOut] = []
        used = 0
        have_plaint = False
        have_ws = False
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
            if d.doc_type == "Plaint":
                have_plaint = True
            if d.doc_type == "Written statement":
                have_ws = True
            role = role_of(d)
            pleading = role in ("Plaint", "Written statement")
            for p in pages:
                if not p.text.strip():
                    continue
                for block in _blocks(p.text):
                    for raw in _SPLIT.split(block):
                        s = raw.replace("\x01", ".").strip()
                        if len(s) < 20:
                            continue
                        figs = None
                        for key, _t, _p, rx in ISSUES:
                            hits = len(rx.findall(s))
                            if not hits:
                                continue
                            if figs is None:
                                figs = figures_of(s)
                            shown = s if len(s) <= 320 else s[:317] + "..."
                            buckets[key].append((
                                0 if pleading else 1, -hits, d.id, p.page_no,
                                PassageOut(
                                    doc_id=d.id, doc_name=d.name, doc_type=d.doc_type, side=d.side,
                                    filed=d.filed, page=p.page_no, method=p.method, role=role,
                                    sentence=shown, figures=figs, denial=bool(_DENIAL.search(s)),
                                ),
                            ))

        out: List[IssueOut] = []
        for key, title, plain, _rx in ISSUES:
            rows = sorted(buckets[key], key=lambda r: (r[0], r[1], r[2], r[3]))
            if not rows:
                continue
            every = [r[4] for r in rows]
            status, text = status_of(every, have_plaint, have_ws)
            picked: List[PassageOut] = []
            per_doc: Dict[int, int] = {}
            for r in rows:
                p = r[4]
                if per_doc.get(p.doc_id, 0) >= MAX_PER_DOC:
                    continue
                per_doc[p.doc_id] = per_doc.get(p.doc_id, 0) + 1
                picked.append(p)
                if len(picked) >= MAX_PER_ISSUE:
                    break
            out.append(IssueOut(
                key=key, title=title, plain=plain, status=status, status_text=text,
                found=len(every), passages=picked,
            ))
        out.sort(key=lambda i: (_ORDER[i.status], -i.found))
        return IssuesOut(
            issues=out, docs_used=used, unread=unread,
            have_plaint=have_plaint, have_written_statement=have_ws,
        )