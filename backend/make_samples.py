import os
import textwrap

from fpdf import FPDF
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "samples")
os.makedirs(OUT, exist_ok=True)


def typed(name, title, pages):
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=18)
    for number, paragraphs in enumerate(pages):
        pdf.add_page()
        if number == 0:
            pdf.set_font("Helvetica", "B", 13)
            for line in title:
                pdf.multi_cell(0, 7, line, align="C", new_x="LMARGIN", new_y="NEXT")
            pdf.ln(5)
        pdf.set_font("Helvetica", "", 11)
        for para in paragraphs:
            pdf.multi_cell(0, 6, para, new_x="LMARGIN", new_y="NEXT")
            pdf.ln(2)
    pdf.output(os.path.join(OUT, name))


def scanned(name, lines):
    width, height = 1654, 2339
    img = Image.new("L", (width, height), 255)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 40)
        bold = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 52)
    except OSError:
        font = bold = ImageFont.load_default()
    y = 220
    for text, big in lines:
        for part in textwrap.wrap(text, width=40 if big else 58) or [""]:
            draw.text((180, y), part, font=bold if big else font, fill=40)
            y += 78 if big else 64
        y += 18
    img = img.rotate(1.1, fillcolor=255, resample=Image.BICUBIC)
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    img.convert("RGB").save(os.path.join(OUT, name), "PDF", resolution=200.0)


typed("01_plaint_typed.pdf", [
    "IN THE COURT OF THE CIVIL JUDGE, CHENNAI",
    "Lakshmi Handlooms (Plaintiff) versus Sundar Garments Private Limited (Defendant)",
    "PLAINT",
], [[
    "1. The plaintiff, Ms. Lakshmi Narayanan, carries on business as Lakshmi Handlooms at Salem. The defendant is a garment manufacturer at Tiruppur.",
    "2. By a written agreement dated 12 March 2025, the plaintiff agreed to supply 5,000 metres of cotton fabric to the defendant at Rs. 180 per metre, a total price of Rs. 9,00,000.",
    "3. The defendant paid an advance of Rs. 3,00,000 on 14 March 2025.",
    "4. Delivery was to be made on or before 30 April 2025.",
], [
    "5. The plaintiff delivered the full quantity of 5,000 metres on 28 April 2025 under Delivery Challan No. 118, which the defendant's staff received without objection.",
    "6. The defendant has not paid the balance of Rs. 6,00,000 despite repeated requests.",
    "7. The plaintiff sent a legal notice dated 20 May 2025. The defendant did not reply.",
    "8. The cause of action arose on 30 April 2025 and again on 20 May 2025, within the jurisdiction of this Court.",
    "PRAYER: The plaintiff prays for a decree for Rs. 6,00,000 with interest at 12 percent per annum from 30 April 2025 until payment, and costs.",
    "Place: Chennai. Date: 02 July 2025. Advocate for the Plaintiff.",
]])

typed("02_written_statement_typed.pdf", [
    "IN THE COURT OF THE CIVIL JUDGE, CHENNAI",
    "WRITTEN STATEMENT OF THE DEFENDANT",
], [[
    "1. The defendant admits the agreement dated 12 March 2025 and the price of Rs. 180 per metre.",
    "2. The defendant admits paying an advance of Rs. 3,00,000 on 14 March 2025.",
    "3. The defendant denies that the full quantity of 5,000 metres was delivered. Only 3,200 metres reached the defendant's factory, on 12 May 2025, which is 12 days after the agreed date of 30 April 2025.",
], [
    "4. Of the 3,200 metres, 400 metres were defective in colour and the defendant rejected them by email dated 15 May 2025.",
    "5. The defendant is liable to pay only for 2,800 metres at Rs. 180, which is Rs. 5,04,000. After adjusting the advance of Rs. 3,00,000, the balance is Rs. 2,04,000, which the defendant is ready to pay.",
    "6. The defendant denies receiving any legal notice dated 20 May 2025.",
    "7. The defendant denies that any cause of action arose on 30 April 2025.",
    "PRAYER: The defendant prays that the suit be dismissed with costs.",
    "Place: Chennai. Date: 05 August 2025. Advocate for the Defendant.",
]])

scanned("03_delivery_challan_scanned.pdf", [
    ("DELIVERY CHALLAN No. 118", True),
    ("Lakshmi Handlooms, Salem", False),
    ("Date: 28 April 2025", False),
    ("To: Sundar Garments Private Limited, Tiruppur", False),
    ("Item: Cotton fabric, white", False),
    ("Quantity: 4,200 metres", False),
    ("Vehicle number: TN 30 AB 1234", False),
    ("Received by: S. Kumar", False),
    ("Remarks: goods received in good condition", False),
])
print("Sample PDFs written to", OUT)