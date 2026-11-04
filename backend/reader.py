import os
import re
import threading
from datetime import datetime
from typing import List

from fastapi import Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import DateTime, Integer, String, Text, UniqueConstraint, delete, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from b3 import UPLOAD_DIR
from database import Base, SessionLocal, get_db
from models import Case, Document, User, utcnow

SUPPORTED = ("application/pdf", "image/jpeg", "image/png")
MAX_PAGES = 60
MAX_OCR_PAGES = 30
MIN_TEXT = 40
MIN_OCR = 15
PAGE_CAP = 15000
STALE_SECONDS = 600
DEFAULT_TESSERACT = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


class DocRead(Base):
    __tablename__ = "doc_reads"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(Integer, unique=True, index=True)
    status: Mapped[str] = mapped_column(String(10), default="reading")
    pages_total: Mapped[int] = mapped_column(Integer, default=0)
    pages_text: Mapped[int] = mapped_column(Integer, default=0)
    pages_ocr: Mapped[int] = mapped_column(Integer, default=0)
    pages_blank: Mapped[int] = mapped_column(Integer, default=0)
    pages_none: Mapped[int] = mapped_column(Integer, default=0)
    message: Mapped[str] = mapped_column(String(255), default="")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class DocPage(Base):
    __tablename__ = "doc_pages"
    __table_args__ = (UniqueConstraint("document_id", "page_no", name="uq_doc_page"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(Integer, index=True)
    page_no: Mapped[int] = mapped_column(Integer)
    method: Mapped[str] = mapped_column(String(8))  # text, ocr, blank or none
    text: Mapped[str] = mapped_column(Text)


class ReadItemOut(BaseModel):
    doc_id: int
    name: str
    doc_type: str
    side: str
    filed: bool
    status: str  # new, reading, done, failed or nofile
    pages_total: int
    pages_text: int
    pages_ocr: int
    pages_blank: int
    pages_none: int
    message: str


class ReadingOut(BaseModel):
    items: List[ReadItemOut]
    ocr_ready: bool
    busy: bool


class PageOut(BaseModel):
    page: int
    method: str
    text: str


class DocPagesOut(BaseModel):
    doc_id: int
    name: str
    pages: List[PageOut]


def _tidy(text: str) -> str:
    text = text.replace("\x00", "")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _tesseract():
    try:
        import pytesseract
    except ImportError:
        return None
    cmd = os.getenv("TESSERACT_CMD", "").strip()
    if not cmd and os.path.exists(DEFAULT_TESSERACT):
        cmd = DEFAULT_TESSERACT
    if cmd:
        pytesseract.pytesseract.tesseract_cmd = cmd
    try:
        pytesseract.get_tesseract_version()
    except Exception:
        return None
    return pytesseract


def _extract(path: str, mime: str):
    tess = _tesseract()
    lang = os.getenv("OCR_LANG", "eng")
    rows = []
    note = ""
    if mime == "application/pdf":
        import pypdfium2 as pdfium

        pdf = pdfium.PdfDocument(path)
        try:
            total = len(pdf)
            if total > MAX_PAGES:
                note = f"Only the first {MAX_PAGES} pages were read."
            ocr_used = 0
            for i in range(min(total, MAX_PAGES)):
                page = pdf[i]
                try:
                    textpage = page.get_textpage()
                    try:
                        text = _tidy(textpage.get_text_range())
                    finally:
                        textpage.close()
                    if len(text) >= MIN_TEXT:
                        rows.append((i + 1, "text", text))
                    elif tess is not None and ocr_used < MAX_OCR_PAGES:
                        ocr_used += 1
                        image = page.render(scale=300 / 72).to_pil()
                        text = _tidy(tess.image_to_string(image, lang=lang, timeout=90))
                        rows.append((i + 1, "ocr" if len(text) >= MIN_OCR else "blank", text))
                    else:
                        rows.append((i + 1, "none", text))
                finally:
                    page.close()
        finally:
            pdf.close()
    else:
        from PIL import Image

        if tess is None:
            rows.append((1, "none", ""))
        else:
            with Image.open(path) as image:
                text = _tidy(tess.image_to_string(image.convert("RGB"), lang=lang, timeout=90))
            rows.append((1, "ocr" if len(text) >= MIN_OCR else "blank", text))
    return rows, note


def read_document(doc_id: int) -> None:
    db = SessionLocal()
    try:
        d = db.get(Document, doc_id)
        rec = db.scalar(select(DocRead).where(DocRead.document_id == doc_id))
        if d is None or rec is None:
            return
        try:
            rows, note = _extract(os.path.join(UPLOAD_DIR, d.stored_name), d.mime)
        except Exception as exc:
            print("Reading failed for document", doc_id, exc)
            rec.status = "failed"
            rec.message = "Could not read this file."
            rec.updated_at = utcnow()
            db.commit()
            return
        db.execute(delete(DocPage).where(DocPage.document_id == doc_id))
        counts = {"text": 0, "ocr": 0, "blank": 0, "none": 0}
        for number, method, text in rows:
            counts[method] += 1
            db.add(DocPage(document_id=doc_id, page_no=number, method=method, text=text[:PAGE_CAP]))
        messages = []
        if counts["none"]:
            messages.append(f"{counts['none']} page(s) need OCR. Install Tesseract, then tap Read all again.")
        if note:
            messages.append(note)
        rec.status = "done"
        rec.pages_total = len(rows)
        rec.pages_text = counts["text"]
        rec.pages_ocr = counts["ocr"]
        rec.pages_blank = counts["blank"]
        rec.pages_none = counts["none"]
        rec.message = " ".join(messages)[:255]
        rec.updated_at = utcnow()
        db.commit()
    finally:
        db.close()


def run_jobs(ids: List[int]) -> None:
    for doc_id in ids:
        try:
            read_document(doc_id)
        except Exception as exc:
            print("Reading job error for document", doc_id, exc)


def install_reader(app, m):
    """Adds the document reading routes. 'm' is main.py, which owns the shared helpers."""

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

    def item_out(d: Document, rec) -> ReadItemOut:
        if not d.stored_name or d.mime not in SUPPORTED:
            status, message = "nofile", ""
        elif rec is None:
            status, message = "new", ""
        else:
            status, message = rec.status, rec.message
            if status == "reading" and (utcnow() - rec.updated_at).total_seconds() > STALE_SECONDS:
                status, message = "failed", "Reading did not finish. Try again."
        return ReadItemOut(
            doc_id=d.id, name=d.name, doc_type=d.doc_type, side=d.side, filed=d.filed, status=status,
            pages_total=rec.pages_total if rec else 0, pages_text=rec.pages_text if rec else 0,
            pages_ocr=rec.pages_ocr if rec else 0, pages_blank=rec.pages_blank if rec else 0,
            pages_none=rec.pages_none if rec else 0, message=message,
        )

    def visible_docs(db: Session, user: User, c: Case) -> List[Document]:
        rows = db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
        return [d for d in rows if can_see(db, user, c, d)]

    def build(db: Session, user: User, c: Case) -> ReadingOut:
        items = []
        for d in visible_docs(db, user, c):
            rec = db.scalar(select(DocRead).where(DocRead.document_id == d.id))
            items.append(item_out(d, rec))
        return ReadingOut(
            items=items, ocr_ready=_tesseract() is not None,
            busy=any(i.status == "reading" for i in items),
        )

    @app.get("/cases/{cid}/reading", response_model=ReadingOut)
    def reading_status(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        return build(db, user, case_for(db, user, cid))

    @app.post("/cases/{cid}/reading", response_model=ReadingOut)
    def start_reading(
            cid: int,
            force: bool = Query(False),
            user: User = Depends(m.approved_user),
            db: Session = Depends(get_db),
    ):
        c = case_for(db, user, cid)
        now = utcnow()
        ids = []
        for d in visible_docs(db, user, c):
            if not d.stored_name or d.mime not in SUPPORTED:
                continue
            rec = db.scalar(select(DocRead).where(DocRead.document_id == d.id))
            if rec and rec.status == "reading" and (now - rec.updated_at).total_seconds() < STALE_SECONDS:
                continue
            if rec and rec.status == "done" and not force:
                continue
            if rec is None:
                rec = DocRead(document_id=d.id)
                db.add(rec)
            rec.status = "reading"
            rec.message = ""
            rec.updated_at = now
            ids.append(d.id)
        if ids:
            m.audit(db, user.id, "Started reading documents", f"{m.label(c)} · {len(ids)} file(s)")
        db.commit()
        if ids:
            threading.Thread(target=run_jobs, args=(ids,), daemon=True).start()
        return build(db, user, c)

    @app.get("/docs/{did}/pages", response_model=DocPagesOut)
    def document_pages(did: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        d = db.get(Document, did)
        if d is None:
            raise HTTPException(status_code=404, detail="Document not found")
        c = case_for(db, user, d.case_id)
        if not can_see(db, user, c, d):
            raise HTTPException(status_code=404, detail="Document not found")
        rows = db.scalars(select(DocPage).where(DocPage.document_id == did).order_by(DocPage.page_no))
        return DocPagesOut(
            doc_id=d.id, name=d.name,
            pages=[PageOut(page=r.page_no, method=r.method, text=r.text) for r in rows],
        )