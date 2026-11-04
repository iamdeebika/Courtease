import json
import os
import re
import threading
import time
from collections import defaultdict, deque
from datetime import datetime
from typing import Dict, List
from urllib.parse import quote

import httpx
from fastapi import Depends, HTTPException, Query
from pydantic import BaseModel, field_validator
from sqlalchemy import DateTime, Integer, String, Text, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from database import Base, get_db
from models import Case, CaseCounsel, User, utcnow

BASE = "https://api.kanoon.dev"
TIMEOUT = 12.0
CACHE_SECONDS = 600
PER_USER_PER_MINUTE = 15
DOC_ID = re.compile(r"^[0-9A-Za-z-]{8,64}$")
NOTICE = "No match in the pilot sample. This does not mean no law or judgment exists."


class SavedPrecedent(Base):
    __tablename__ = "saved_precedents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    case_id: Mapped[int] = mapped_column(Integer, default=0, index=True)
    ref: Mapped[str] = mapped_column(String(80), default="")
    title: Mapped[str] = mapped_column(String(300))
    court: Mapped[str] = mapped_column(String(120), default="")
    kind: Mapped[str] = mapped_column(String(40), default="")
    doc_date: Mapped[str] = mapped_column(String(30), default="")
    snippet: Mapped[str] = mapped_column(Text, default="")
    page: Mapped[int] = mapped_column(Integer, default=0)
    source_url: Mapped[str] = mapped_column(String(600), default="")
    page_url: Mapped[str] = mapped_column(String(600), default="")
    note: Mapped[str] = mapped_column(String(500), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


# ---------- shapes ----------

class LawResultOut(BaseModel):
    ref: str
    title: str
    court: str
    kind: str
    date: str
    snippet: str
    page: int
    source_url: str
    page_url: str


class LawSearchOut(BaseModel):
    status: str
    message: str
    results: List[LawResultOut]
    next_offset: int
    retry_after: int
    fallback_url: str


class PageItemOut(BaseModel):
    page: int
    text: str


class PagesOut(BaseModel):
    status: str
    message: str
    pages: List[PageItemOut]
    next_after: int
    retry_after: int


class SavedIn(BaseModel):
    case_id: int = 0
    ref: str = ""
    title: str
    court: str = ""
    kind: str = ""
    date: str = ""
    snippet: str = ""
    page: int = 0
    source_url: str = ""
    page_url: str = ""
    note: str = ""

    @field_validator("title")
    @classmethod
    def _title(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("The result has no title")
        return v[:300]

    @field_validator("case_id", "page")
    @classmethod
    def _positive(cls, v: int) -> int:
        return max(v, 0)

    @field_validator("ref")
    @classmethod
    def _ref(cls, v: str) -> str:
        return v.strip()[:80]

    @field_validator("court")
    @classmethod
    def _court(cls, v: str) -> str:
        return v.strip()[:120]

    @field_validator("kind")
    @classmethod
    def _kind(cls, v: str) -> str:
        return v.strip()[:40]

    @field_validator("date")
    @classmethod
    def _date(cls, v: str) -> str:
        return v.strip()[:30]

    @field_validator("snippet")
    @classmethod
    def _snippet(cls, v: str) -> str:
        return v.strip()[:2000]

    @field_validator("note")
    @classmethod
    def _note(cls, v: str) -> str:
        return v.strip()[:500]

    @field_validator("source_url", "page_url")
    @classmethod
    def _url(cls, v: str) -> str:
        v = v.strip()[:600]
        return v if v.startswith(("https://", "http://")) else ""


class SavedOut(BaseModel):
    id: int
    case_id: int
    ref: str
    title: str
    court: str
    kind: str
    date: str
    snippet: str
    page: int
    source_url: str
    page_url: str
    note: str
    created_at: int


# ---------- reading the upstream reply (tolerant of small differences) ----------

def _pick(d: dict, keys) -> str:
    for k in keys:
        v = d.get(k)
        if isinstance(v, (str, int, float)) and not isinstance(v, bool) and str(v).strip():
            return str(v).strip()
    return ""


def _flat(item: dict) -> dict:
    merged: dict = {}
    for v in item.values():
        if isinstance(v, dict):
            merged.update(v)
    merged.update(item)
    return merged


def _clean(text: str, limit: int) -> str:
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit]


def _url(value: str) -> str:
    return value if value.startswith(("https://", "http://")) else ""


def _page_no(d: dict) -> int:
    for k in ("page", "page_number", "matching_page", "page_no", "matched_page", "number"):
        v = d.get(k)
        if isinstance(v, dict):
            v = v.get("number") or v.get("page") or v.get("page_number") or v.get("page_no")
        try:
            n = int(v)
            if n > 0:
                return n
        except (TypeError, ValueError):
            continue
    return 0


def _items(body, keys=("results", "data", "items", "hits", "documents")) -> list:
    if isinstance(body, list):
        return [x for x in body if isinstance(x, dict)]
    if isinstance(body, dict):
        for k in keys:
            v = body.get(k)
            if isinstance(v, list):
                return [x for x in v if isinstance(x, dict)]
    return []


def _next_offset(body) -> int:
    if isinstance(body, dict):
        v = body.get("next_offset")
        if isinstance(v, int) and v >= 0:
            return v
        link = body.get("next_url")
        if isinstance(link, str):
            m = re.search(r"offset=(\d+)", link)
            if m:
                return int(m.group(1))
    return -1


def _normalize(item: dict) -> LawResultOut:
    m = _flat(item)
    document_url = _url(_pick(m, ["document_url"]))
    ref = _pick(m, ["document_id", "doc_id", "id"])
    if not ref and document_url:
        ref = document_url.rstrip("/").split("/")[-1]
    if not DOC_ID.match(ref):
        ref = ""
    court = _pick(m, ["court_name", "court", "court_id", "bench"])
    kind = _pick(m, ["kind", "document_kind", "type"])
    date = _pick(m, ["document_date", "date", "decided_at", "order_date", "published_at"])
    title = _pick(m, ["title", "case_title", "caption", "document_title", "name"])
    if not title:
        parts = [kind.title() if kind else "Court document", court, date]
        title = " · ".join(p for p in parts if p)
    return LawResultOut(
        ref=ref,
        title=title[:300],
        court=court[:120],
        kind=kind[:40],
        date=date[:30],
        snippet=_clean(_pick(m, ["snippet", "highlight", "excerpt", "text", "content"]), 600),
        page=_page_no(m),
        source_url=_url(_pick(m, ["source_url"])),
        page_url=_url(_pick(m, ["matching_page_url", "pages_url"])),
    )


# ---------- calling kanoon.dev with a cache and politeness ----------

_cache: Dict[str, tuple] = {}
_lock = threading.Lock()
_user_hits: Dict[int, deque] = defaultdict(deque)


def _allow(user_id: int) -> bool:
    now = time.time()
    q = _user_hits[user_id]
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= PER_USER_PER_MINUTE:
        return False
    q.append(now)
    return True


def _get(path: str, params: dict):
    """Returns (status, body, wait). Status: ok, busy, unavailable or error."""
    key = path + "?" + "&".join(f"{k}={params[k]}" for k in sorted(params))
    now = time.time()
    with _lock:
        hit = _cache.get(key)
        if hit and now - hit[0] < CACHE_SECONDS:
            return "ok", hit[1], 0
    try:
        r = httpx.get(
            BASE + path, params=params, timeout=TIMEOUT,
            headers={"User-Agent": "CourtEase-college-project", "Accept": "application/json"},
            )
    except httpx.HTTPError:
        return "unavailable", None, 0
    if r.status_code == 429:
        try:
            wait = int(r.headers.get("Retry-After", "10"))
        except ValueError:
            wait = 10
        return "busy", None, max(wait, 1)
    if r.status_code >= 500:
        return "unavailable", None, 0
    try:
        body = r.json()
    except ValueError:
        return "unavailable", None, 0
    if isinstance(body, dict) and body.get("error"):
        if body.get("error") == "temporarily_unavailable":
            return "unavailable", None, 0
        return "error", body, 0
    if r.status_code >= 400:
        return "error", body, 0
    with _lock:
        _cache[key] = (now, body)
        if len(_cache) > 300:
            oldest = min(_cache, key=lambda k: _cache[k][0])
            _cache.pop(oldest, None)
    return "ok", body, 0


def _status_message(status: str, body, wait: int) -> str:
    if status == "busy":
        return f"The law database is busy. Try again in {wait} seconds."
    if status == "unavailable":
        return "The law database is not available right now. Try again later, or search on the Indian Kanoon website."
    if isinstance(body, dict) and body.get("message"):
        return str(body["message"])[:200]
    return "The law database could not answer this search."


def _sample_cards(q: str, fallback: str) -> List[LawResultOut]:
    """Placeholder cards for testing the screen while the real service is down. Only used when LAW_DEMO=1."""
    cards = []
    for n, topic in enumerate(["layout test", "saving test"], start=1):
        cards.append(LawResultOut(
            ref="", title=f"SAMPLE {n} · {q.title()}: {topic}", court="Sample court", kind="sample",
            date="", snippet="This card is a placeholder for testing the screen. It is not a real judgment.",
            page=0, source_url=fallback, page_url="",
        ))
    return cards


# ---------- keeping case details out of searches ----------

RISKY = [
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"), "an email address"),
    (re.compile(r"(?<!\d)\+?\d[\d\s-]{8,}\d(?!\d)"), "a phone number"),
    (re.compile(r"\b(os|cs|oa|wp|crl|ca)\s*\d+\s*/\s*\d{4}\b", re.I), "a case number"),
    (re.compile(r"\bce-[a-z0-9]{5}\b", re.I), "a case code"),
]


def install_law(app, m):
    """Adds the law search and saved precedent routes. 'm' is main.py, which owns the shared helpers."""

    def research_user(user: User = Depends(m.approved_user)) -> User:
        if user.role not in ("lawyer", "judge"):
            raise HTTPException(status_code=403, detail="Only advocates and judges can use the law search")
        return user

    def check_query(db: Session, user: User, q: str) -> None:
        for rx, label in RISKY:
            if rx.search(q):
                raise HTTPException(status_code=400, detail=f"Don't put {label} in a search. Use legal topics only.")
        low = q.lower()
        if user.role == "judge":
            cases = db.scalars(select(Case).where(Case.judge_id == user.id))
        else:
            ids = select(CaseCounsel.case_id).where(CaseCounsel.user_id == user.id)
            cases = db.scalars(select(Case).where(Case.id.in_(ids)))
        for c in cases:
            for name in (c.claimant, c.respondent):
                name = (name or "").strip().lower()
                if len(name) >= 4 and name in low:
                    raise HTTPException(
                        status_code=400, detail="Don't put party names in a search. Use legal topics only."
                    )

    @app.get("/law/search", response_model=LawSearchOut)
    def law_search(
            q: str = Query(..., min_length=2, max_length=200),
            court: str = Query("", max_length=20),
            kind: str = Query("", max_length=20),
            offset: int = Query(0, ge=0, le=500),
            user: User = Depends(research_user),
            db: Session = Depends(get_db),
    ):
        q = q.strip()
        if len(q) < 2:
            raise HTTPException(status_code=400, detail="Type at least 2 letters")
        if court and not re.fullmatch(r"[A-Za-z0-9_]{2,20}", court):
            raise HTTPException(status_code=400, detail="Invalid court filter")
        if kind and not re.fullmatch(r"[a-z_]{2,20}", kind):
            raise HTTPException(status_code=400, detail="Invalid document kind")
        check_query(db, user, q)
        if not _allow(user.id):
            raise HTTPException(status_code=429, detail="Too many searches. Wait a minute and try again.")

        fallback = "https://indiankanoon.org/search/?formInput=" + quote(q)
        params: dict = {"q": q, "limit": 10, "offset": offset}
        if court:
            params["court"] = court
        if kind:
            params["kind"] = kind
        status, body, wait = _get("/v1/search", params)
        if status != "ok":
            message = _status_message(status, body, wait)
            demo: List[LawResultOut] = []
            if os.getenv("LAW_DEMO") == "1":
                message += " Showing SAMPLE cards so you can test the screen. They are not real judgments."
                demo = _sample_cards(q, fallback)
            return LawSearchOut(
                status=status, message=message, results=demo,
                next_offset=-1, retry_after=wait, fallback_url=fallback,
            )
        results = [_normalize(i) for i in _items(body)]
        return LawSearchOut(
            status="ok", message="" if results else NOTICE, results=results,
            next_offset=_next_offset(body), retry_after=0, fallback_url=fallback,
        )

    @app.get("/law/document/{doc_id}/pages", response_model=PagesOut)
    def law_pages(
            doc_id: str,
            after: int = Query(0, ge=0, le=100000),
            limit: int = Query(1, ge=1, le=5),
            user: User = Depends(research_user),
    ):
        if not DOC_ID.match(doc_id):
            raise HTTPException(status_code=400, detail="Invalid document")
        if not _allow(user.id):
            raise HTTPException(status_code=429, detail="Too many requests. Wait a minute and try again.")
        status, body, wait = _get(f"/v1/documents/{doc_id}/pages", {"after": after, "limit": limit})
        if status != "ok":
            return PagesOut(
                status=status, message=_status_message(status, body, wait),
                pages=[], next_after=-1, retry_after=wait,
            )
        pages = []
        for item in _items(body, ("pages", "results", "data", "items")):
            text = _clean(_pick(item, ["text", "content", "extracted_text"]), 6000)
            if text:
                pages.append(PageItemOut(page=_page_no(item), text=text))
        nxt = body.get("next_after") if isinstance(body, dict) else None
        return PagesOut(
            status="ok", message="", pages=pages,
            next_after=nxt if isinstance(nxt, int) and nxt >= 0 else -1, retry_after=0,
        )

    @app.get("/law/debug")
    def law_debug(path: str = "search", q: str = "bail", reg: User = Depends(m.registry_user)):
        if path == "coverage":
            status, body, wait = _get("/v1/coverage", {})
        else:
            status, body, wait = _get("/v1/search", {"q": q[:200], "limit": 2})
        text = json.dumps(body)[:4000] if body is not None else ""
        return {"status": status, "retry_after": wait, "body": text}

    # ---------- saved precedents (private to the person who saved them) ----------

    def saved_out(p: SavedPrecedent) -> SavedOut:
        return SavedOut(
            id=p.id, case_id=p.case_id, ref=p.ref, title=p.title, court=p.court, kind=p.kind,
            date=p.doc_date, snippet=p.snippet, page=p.page, source_url=p.source_url,
            page_url=p.page_url, note=p.note, created_at=m.millis(p.created_at),
        )

    def check_case(db: Session, user: User, case_id: int) -> None:
        if case_id == 0:
            return
        c = db.get(Case, case_id)
        ok = c is not None and (
                (user.role == "judge" and c.judge_id == user.id)
                or (user.role == "lawyer" and m.side_of(db, c.id, user.id) is not None)
        )
        if not ok:
            raise HTTPException(status_code=404, detail="Case not found")

    @app.post("/precedents", response_model=SavedOut, status_code=201)
    def save_precedent(body: SavedIn, user: User = Depends(research_user), db: Session = Depends(get_db)):
        check_case(db, user, body.case_id)
        dup = db.scalar(select(SavedPrecedent.id).where(
            SavedPrecedent.user_id == user.id, SavedPrecedent.case_id == body.case_id,
            SavedPrecedent.title == body.title, SavedPrecedent.page == body.page,
            ))
        if dup:
            raise HTTPException(status_code=400, detail="Already saved for this case")
        p = SavedPrecedent(
            user_id=user.id, case_id=body.case_id, ref=body.ref, title=body.title, court=body.court,
            kind=body.kind, doc_date=body.date, snippet=body.snippet, page=body.page,
            source_url=body.source_url, page_url=body.page_url, note=body.note,
        )
        db.add(p)
        m.audit(db, user.id, "Saved precedent", body.title)
        db.commit()
        db.refresh(p)
        return saved_out(p)

    @app.get("/precedents", response_model=List[SavedOut])
    def list_precedents(
            case_id: int = Query(-1),
            user: User = Depends(research_user),
            db: Session = Depends(get_db),
    ):
        stmt = select(SavedPrecedent).where(SavedPrecedent.user_id == user.id)
        if case_id >= 0:
            stmt = stmt.where(SavedPrecedent.case_id == case_id)
        return [saved_out(p) for p in db.scalars(stmt.order_by(SavedPrecedent.id.desc()))]

    @app.delete("/precedents/{pid}", response_model=m.MessageOut)
    def delete_precedent(pid: int, user: User = Depends(research_user), db: Session = Depends(get_db)):
        p = db.get(SavedPrecedent, pid)
        if p is None or p.user_id != user.id:
            raise HTTPException(status_code=404, detail="Saved precedent not found")
        db.delete(p)
        db.commit()
        return m.MessageOut(message="Removed")