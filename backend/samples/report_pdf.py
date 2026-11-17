import os
import re

from fpdf import FPDF

FONT_DIR = os.getenv("REPORT_FONT_DIR", r"C:\Windows\Fonts")
FONT_FILES = {"": "arial.ttf", "B": "arialbd.ttf", "I": "ariali.ttf"}
NAVY = (16, 42, 67)
TEAL = (14, 124, 123)
GREY = (105, 112, 120)
RED = (176, 42, 55)


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