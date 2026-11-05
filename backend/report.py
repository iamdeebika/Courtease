import os
import re
from datetime import datetime
from typing import List

from fastapi import Depends, HTTPException
from fastapi.responses import Response
from fpdf import FPDF
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from flags import nice
from law import SavedPrecedent
from models import Case, Document, Order, User
from summary import endpoint_of, leaks

FONT_DIR = os.getenv("REPORT_FONT_DIR", r"C:\Windows\Fonts")
FONT_FILES = {"": "arial.ttf", "B": "arialbd.ttf", "I": "ariali.ttf"}
NAVY = (16, 42, 67)
TEAL = (14, 124, 123)
GREY = (105, 112, 120)
RED = (176, 42, 55)
MAX_EVENTS = 80

STATUS_TEXT = {
    "disputed": "Appears disputed",
    "both": "Both pleadings mention it",
    "claimant_only": "Plaint only",
    "respondent_only": "Written statement only",
    "evidence_only": "Other documents only",
}
KIND_TITLES = {
    "amount": "Money amounts differ",
    "quantity": "Quantities differ",
    "date": "Dates differ",
    "denied": "Denied in the written statement",
    "unsupported": "No supporting document found",
}
KIND_ORDER = ["amount", "quantity", "date", "denied", "unsupported"]


def fonts_ready() -> bool:
    return all(os.path.isfile(os.path.join(FONT_DIR, f)) for f in FONT_FILES.values())


def _keep(ch: str) -> bool:
    o = ord(ch)
    return ch == "\n" or 32 <= o <= 126 or 0xA0 <= o <= 0x24F or 0x2010 <= o <= 0x2044 or o in (0x20B9, 0x2192)


def safe(text: str, unicode_ok: bool) -> str:
    text = text.replace("\r", "").replace("\t", " ").replace("\u2011", "-").replace("\u00ad", "")
    if unicode_ok:
        out = "".join(ch if _keep(ch) else "?" for ch in text)
    else:
        text = text.replace("\u20b9", "Rs. ")
        out = text.encode("latin-1", "replace").decode("latin-1")
    return re.sub(r"\?{2,}", "?", out)


def soft(text: str) -> str:
    """Breaks very long unbroken strings (like web links) so they always fit on the page."""
    return re.sub(
        r"\S{50,}",
        lambda mt: " ".join(mt.group(0)[i:i + 45] for i in range(0, len(mt.group(0)), 45)),
        text,
    )


class Report(FPDF):
    def __init__(self, unicode_ok: bool, note: str):
        super().__init__(format="A4")
        self.unicode_ok = unicode_ok
        self.note = note
        self.family = "Helvetica"
        if unicode_ok:
            for style, name in FONT_FILES.items():
                self.add_font("Arial", style, os.path.join(FONT_DIR, name))
            self.family = "Arial"
        self.set_margins(15, 15, 15)
        self.set_auto_page_break(auto=True, margin=18)
        self.alias_nb_pages()

    def s(self, text) -> str:
        return soft(safe(str(text or ""), self.unicode_ok))

    def footer(self):
        self.set_y(-14)
        self.set_font(self.family, "I", 8)
        self.set_text_color(*GREY)
        self.cell(140, 5, self.s(self.note), align="L")
        self.cell(0, 5, f"Page {self.page_no()} of {{nb}}", align="R")

    def keep(self, height: float):
        if self.get_y() + height > self.h - 22:
            self.add_page()

    def h1(self, text: str):
        self.set_font(self.family, "B", 18)
        self.set_text_color(*NAVY)
        self.multi_cell(0, 9, self.s(text), new_x="LMARGIN", new_y="NEXT")

    def h2(self, text: str):
        self.keep(30)
        self.ln(5)
        self.set_font(self.family, "B", 12.5)
        self.set_text_color(*TEAL)
        self.multi_cell(0, 7, self.s(text), new_x="LMARGIN", new_y="NEXT")
        y = self.get_y()
        self.set_draw_color(200, 210, 215)
        self.line(self.l_margin, y, self.w - self.r_margin, y)
        self.ln(2.5)

    def h3(self, text: str):
        self.keep(22)
        self.ln(2)
        self.set_font(self.family, "B", 10.5)
        self.set_text_color(*NAVY)
        self.multi_cell(0, 5.5, self.s(text), new_x="LMARGIN", new_y="NEXT")

    def body(self, text: str, size: float = 10, color=NAVY, style: str = ""):
        self.set_font(self.family, style, size)
        self.set_text_color(*color)
        self.multi_cell(0, 5.2, self.s(text), new_x="LMARGIN", new_y="NEXT")

    def small(self, text: str):
        self.set_font(self.family, "I", 8.5)
        self.set_text_color(*GREY)
        self.multi_cell(0, 4.6, self.s(text), new_x="LMARGIN", new_y="NEXT")

    def indent(self, text: str, size: float = 9.8, color=NAVY, style: str = ""):
        self.set_x(self.l_margin + 5)
        self.set_font(self.family, style, size)
        self.set_text_color(*color)
        self.multi_cell(0, 5, self.s(text), new_x="LMARGIN", new_y="NEXT")

    def kv(self, label: str, value: str):
        self.keep(10)
        self.set_x(self.l_margin)
        self.set_font(self.family, "B", 9.5)
        self.set_text_color(*GREY)
        self.cell(40, 5.5, self.s(label))
        self.set_font(self.family, "", 10)
        self.set_text_color(*NAVY)
        self.multi_cell(0, 5.5, self.s(value) or "-", new_x="LMARGIN", new_y="NEXT")

    def box(self, text: str, warn: bool = False):
        self.set_fill_color(*((253, 236, 238) if warn else (232, 244, 243)))
        self.set_font(self.family, "B" if warn else "", 9.5)
        self.set_text_color(*(RED if warn else NAVY))
        self.multi_cell(0, 5.6, self.s(text), fill=True, new_x="LMARGIN", new_y="NEXT")
        self.ln(2)

    def passage(self, role: str, sentence: str, source: str, extra: str = ""):
        self.keep(26)
        self.indent(role, 9, TEAL, "B")
        self.indent(sentence)
        if extra:
            self.indent(extra, 9, TEAL)
        self.indent(source, 8.5, GREY, "I")
        self.ln(1.5)


def source_line(name: str, page: int, filed: bool, side: str = "") -> str:
    parts = [name, f"page {page}"]
    if side:
        parts.append(side.title())
    parts.append("Filed" if filed else "Draft")
    return "Source: " + " · ".join(parts)


def build_pdf(*, judge_view, user, c, claimant, respondent, judge_name, docs, orders, tl, iss, fl,
              precedents, has_drafts, unicode_ok, label, stamp) -> bytes:
    pdf = Report(unicode_ok, "Automated assistance. Check every item against the source.")
    pdf.add_page()

    pdf.h1("Bench brief" if judge_view else "Case report")
    pdf.body(f"{label} · {c.title}", 12, NAVY, "B")
    pdf.small(f"Prepared for {user.full_name} on {stamp}")
    pdf.ln(2)

    if judge_view:
        pdf.box("Built only from documents that were filed in court. Drafts never appear in a judge's brief.")
    elif has_drafts:
        pdf.box(
            "This report includes your side's UNFILED drafts. It is private to your side. "
            "Do not hand it to the court or to the other side.", warn=True,
        )
    else:
        pdf.box("Built from your side's documents and the documents the other side has filed.")

    pdf.h2("Case details")
    pdf.kv("Case", label)
    pdf.kv("Title", c.title)
    pdf.kv("Type", c.case_type.split("· ", 1)[-1])
    pdf.kv("Court", c.court + (f", {c.courtroom}" if c.courtroom else ""))
    pdf.kv("Stage", c.status.title())
    pdf.kv("Claimant", f"{c.claimant}" + (f"  (advocate {claimant})" if claimant else ""))
    pdf.kv("Respondent", f"{c.respondent}" + (f"  (advocate {respondent})" if respondent else "  (advocate has not appeared)"))
    if judge_name:
        pdf.kv("Before", judge_name)
    if c.filed_at:
        pdf.kv("Filed on", c.filed_at.strftime("%d %b %Y"))
    if c.response_due:
        pdf.kv("Response due", c.response_due.strftime("%d %b %Y"))

    pdf.h2("Filed record" if judge_view else "Documents covered")
    if not docs:
        pdf.body("No documents.")
    for d in docs:
        pdf.indent(
            f"• {d.name} — {d.doc_type}, {d.side.title()}, {'Filed' if d.filed else 'Draft'}, {d.pages} page(s)"
        )
    if tl.unread:
        pdf.ln(1)
        pdf.body("Not covered, because they were not read yet:", 9.5, RED, "B")
        for u in tl.unread:
            pdf.indent(f"• {u.name} ({u.reason})", 9.5, RED)

    pdf.h2("Orders")
    if not orders:
        pdf.body("No orders have been issued.")
    for o in orders:
        pdf.keep(24)
        pdf.h3(f"{o.kind}: {o.title}")
        pdf.small(o.created_at.strftime("%d %b %Y"))
        pdf.indent(o.body)

    pdf.h2(f"Timeline ({len(tl.events)} dated event(s))")
    if not tl.events:
        pdf.body("No dates were found. Read the documents first, or the text may contain no dates.")
    for e in tl.events[:MAX_EVENTS]:
        pdf.keep(24)
        pdf.set_font(pdf.family, "B", 10.5)
        pdf.set_text_color(*NAVY)
        pdf.multi_cell(0, 5.5, pdf.s(f"{nice(e.date_iso)} · {e.label}"), new_x="LMARGIN", new_y="NEXT")
        pdf.indent(e.sentence)
        if e.amounts:
            pdf.indent("Amounts mentioned: " + ", ".join(e.amounts), 9, TEAL)
        pdf.indent(source_line(e.doc_name, e.page, e.filed, e.side), 8.5, GREY, "I")
        pdf.ln(1.5)
    if len(tl.events) > MAX_EVENTS:
        pdf.small(f"Showing the first {MAX_EVENTS} of {len(tl.events)} events.")

    pdf.h2(f"Possible issues ({len(iss.issues)})")
    pdf.small("Found by keyword rules, not by a lawyer or a judge. A word can appear without the sides really disputing the point.")
    if not iss.issues:
        pdf.body("No issues were found.")
    for issue in iss.issues:
        pdf.h3(f"{issue.title} · {STATUS_TEXT.get(issue.status, issue.status)}")
        pdf.indent(issue.status_text, 9.5)
        for p in issue.passages[:3]:
            extra = ("Figures: " + ", ".join(p.figures)) if p.figures else ""
            pdf.passage(p.role + ("" if p.filed else " (draft)"), p.sentence,
                        source_line(p.doc_name, p.page, p.filed), extra)

    pdf.h2(f"Points to verify ({len(fl.flags)})")
    pdf.small("These are points to check, not findings. The report never says who is right.")
    if not fl.flags:
        pdf.body("Nothing to verify was found by the rules. That does not mean the documents agree on everything.")
    for kind in KIND_ORDER:
        group = [f for f in fl.flags if f.kind == kind]
        for f in group:
            pdf.h3(f"{f.title}")
            pdf.indent("Statement requires verification. " + f.summary, 9.5)
            if f.claimant_values or f.respondent_values:
                pdf.indent("Claimant's side: " + (", ".join(f.claimant_values) or "-"), 9.5, NAVY, "B")
                pdf.indent("Respondent's side: " + (", ".join(f.respondent_values) or "-"), 9.5, NAVY, "B")
            for p in f.claimant[:2]:
                pdf.passage("Claimant's side · " + p.role, p.sentence, source_line(p.doc_name, p.page, p.filed))
            for p in f.respondent[:2]:
                pdf.passage("Respondent's side · " + p.role, p.sentence, source_line(p.doc_name, p.page, p.filed))

    pdf.h2(f"Saved precedents ({len(precedents)})")
    pdf.small("Only the person who saved a precedent sees it in their own report.")
    if not precedents:
        pdf.body("No precedents were saved for this case.")
    for p in precedents:
        pdf.keep(26)
        pdf.h3(p.title)
        meta = " · ".join(x for x in [p.court, p.doc_date, ("page " + str(p.page)) if p.page else ""] if x)
        if meta:
            pdf.indent(meta, 9.5, GREY)
        if p.snippet:
            pdf.indent(p.snippet)
        if p.note:
            pdf.indent("Note: " + p.note, 9.5, TEAL)
        if p.source_url:
            pdf.indent("Source (kanoon.dev): " + p.source_url, 8.5, GREY, "I")
        pdf.ln(1.5)

    pdf.h2("About this report")
    pdf.body(
        "This report was produced by simple rules on the CourtEase server: keyword and pattern matching on the "
        "text of the documents, with OCR for scanned pages. No outside AI service saw any of this text. It can miss "
        "dates and issues, and OCR can misread characters. It is not legal advice or a finding of any kind. Open "
        "the original page named under each item and check it before relying on anything here.",
        9.5, GREY,
    )
    if not unicode_ok or True:
        pdf.small("Text in scripts other than English may print as ? in this PDF. The source page has the original.")
    return bytes(pdf.output())


def install_report(app, m):
    """Adds the report route. 'm' is main.py, which owns the shared helpers."""

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

    def lead_name(db: Session, ids: list) -> str:
        if not ids:
            return ""
        u = db.get(User, ids[0])
        return u.full_name if u else ""

    @app.get("/cases/{cid}/report")
    def case_report(cid: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        c = case_for(db, user, cid)
        docs = [
            d for d in db.scalars(select(Document).where(Document.case_id == c.id).order_by(Document.id))
            if can_see(db, user, c, d)
        ]
        tl = endpoint_of(app, "/cases/{cid}/timeline")(cid=cid, user=user, db=db)
        iss = endpoint_of(app, "/cases/{cid}/issues")(cid=cid, user=user, db=db)
        fl = endpoint_of(app, "/cases/{cid}/flags")(cid=cid, user=user, db=db)

        judge_view = user.role == "judge"
        my_side = m.side_of(db, c.id, user.id) if user.role == "lawyer" else None
        if leaks(my_side, tl, iss, fl):
            m.audit(db, user.id, "Analysis privacy check failed", m.label(c))
            db.commit()
            raise HTTPException(status_code=500, detail="A safety check stopped this report. Nothing was produced.")

        teams = m.teams_of(db, c.id)
        orders = list(db.scalars(select(Order).where(Order.case_id == c.id).order_by(Order.id)))
        precedents = list(db.scalars(
            select(SavedPrecedent)
            .where(SavedPrecedent.user_id == user.id, SavedPrecedent.case_id == c.id)
            .order_by(SavedPrecedent.id)
        ))
        judge = db.get(User, c.judge_id) if c.judge_id else None
        has_drafts = any((not d.filed) and d.side == my_side for d in docs)
        label = m.label(c)

        data = build_pdf(
            judge_view=judge_view, user=user, c=c,
            claimant=lead_name(db, teams["claimant"]), respondent=lead_name(db, teams["respondent"]),
            judge_name=judge.full_name if judge else "", docs=docs, orders=orders,
            tl=tl, iss=iss, fl=fl, precedents=precedents, has_drafts=has_drafts,
            unicode_ok=fonts_ready(), label=label,
            stamp=datetime.now().strftime("%d %b %Y, %I:%M %p"),
        )

        m.audit(db, user.id, "Exported bench brief" if judge_view else "Exported case report", label)
        db.commit()
        kind = "BenchBrief" if judge_view else "CaseReport"
        name = re.sub(r"[^A-Za-z0-9_-]+", "_", f"CourtEase_{kind}_{label}") + ".pdf"
        return Response(
            content=data, media_type="application/pdf",
            headers={"Content-Disposition": f'attachment; filename="{name}"', "Cache-Control": "no-store"},
        )