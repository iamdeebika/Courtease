import re
import threading
import time
from collections import defaultdict, deque
from datetime import datetime
from typing import Dict, List

import httpx
from pydantic import BaseModel, field_validator
from sqlalchemy import DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from database import Base
from models import utcnow

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

def pick(d: dict, keys) -> str:
    for k in keys:
        v = d.get(k)
        if isinstance(v, (str, int, float)) and not isinstance(v, bool) and str(v).strip():
            return str(v).strip()
    return ""


def flat(item: dict) -> dict:
    merged: dict = {}
    for v in item.values():
        if isinstance(v, dict):
            merged.update(v)
    merged.update(item)
    return merged


def clean(text: str, limit: int) -> str:
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit]


def as_url(value: str) -> str:
    return value if value.startswith(("https://", "http://")) else ""


def page_no(d: dict) -> int:
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


def items_of(body, keys=("results", "data", "items", "hits", "documents")) -> list:
    if isinstance(body, list):
        return [x for x in body if isinstance(x, dict)]
    if isinstance(body, dict):
        for k in keys:
            v = body.get(k)
            if isinstance(v, list):
                return [x for x in v if isinstance(x, dict)]
    return []


def next_offset_of(body) -> int:
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


def normalize(item: dict) -> LawResultOut:
    m = flat(item)
    document_url = as_url(pick(m, ["document_url"]))
    ref = pick(m, ["document_id", "doc_id", "id"])
    if not ref and document_url:
        ref = document_url.rstrip("/").split("/")[-1]
    if not DOC_ID.match(ref):
        ref = ""
    court = pick(m, ["court_name", "court", "court_id", "bench"])
    kind = pick(m, ["kind", "document_kind", "type"])
    date = pick(m, ["document_date", "date", "decided_at", "order_date", "published_at"])
    title = pick(m, ["title", "case_title", "caption", "document_title", "name"])
    if not title:
        parts = [kind.title() if kind else "Court document", court, date]
        title = " · ".join(p for p in parts if p)
    return LawResultOut(
        ref=ref,
        title=title[:300],
        court=court[:120],
        kind=kind[:40],
        date=date[:30],
        snippet=clean(pick(m, ["snippet", "highlight", "excerpt", "text", "content"]), 600),
        page=page_no(m),
        source_url=as_url(pick(m, ["source_url"])),
        page_url=as_url(pick(m, ["matching_page_url", "pages_url"])),
    )


# ---------- calling kanoon.dev with a cache and politeness ----------

_cache: Dict[str, tuple] = {}
_lock = threading.Lock()
_user_hits: Dict[int, deque] = defaultdict(deque)


def allow(user_id: int) -> bool:
    now = time.time()
    q = _user_hits[user_id]
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= PER_USER_PER_MINUTE:
        return False
    q.append(now)
    return True


def fetch(path: str, params: dict):
    """Returns (status, body, wait). Status: ok, busy, unavailable or error."""
    key = path + "?" + "&".join(f"{k}={params[k]}" for k in sorted(params))
    now = time.time