"""End-to-end smoke test of the CourtEase server. Run it while the server is running:

    python smoke_test.py

It creates one test lawyer account and one test case, and walks the whole court workflow.
"""
import sys
import time
from datetime import datetime

import httpx
from fpdf import FPDF

BASE = "http://127.0.0.1:8000"
client = httpx.Client(base_url=BASE, timeout=60)
results = []
stamp = datetime.now().strftime("%m%d%H%M%S")


def check(name, ok, detail=""):
    results.append(bool(ok))
    line = ("PASS  " if ok else "FAIL  ") + name
    if not ok and detail != "":
        line += "   -> " + str(detail)[:200]
    print(line)


def login(email, password):
    r = client.post("/auth/login", json={"email": email, "password": password})
    if r.status_code != 200:
        sys.exit("Could not sign in as %s: %s %s" % (email, r.status_code, r.text[:200]))
    data = r.json()
    return {"Authorization": "Bearer " + data["access_token"]}, data["user"]


def pdf_bytes(title, lines):
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Helvetica", "B", 14)
    pdf.cell(0, 10, title, new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("Helvetica", "", 11)
    for line in lines:
        pdf.multi_cell(0, 6, line, new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())


def upload(h, cid, doc_type, name, data):
    return client.post(
        "/cases/%d/upload" % cid, headers=h, data={"doc_type": doc_type, "pages": "1"},
        files={"file": (name, data, "application/pdf")},
        )


def read_all(h, cid):
    client.post("/cases/%d/reading" % cid, headers=h)
    for _ in range(90):
        time.sleep(1)
        r = client.get("/cases/%d/reading" % cid, headers=h)
        if r.status_code == 200 and not r.json()["busy"]:
            return r.json()
    return None


def user_id(h, email):
    for u in client.get("/sync/users", headers=h).json():
        if u["email"] == email:
            return u["id"]
    return None


def visible_doc_ids(h):
    return {d["id"] for d in client.get("/sync/cases", headers=h).json()["docs"]}


PLAINT = pdf_bytes("PLAINT", [
    "The agreement was signed on 12 March 2025 for Rs. 9,00,000.",
    "The goods were delivered on 28 April 2025 under Delivery Challan No. 118.",
    "The defendant has not paid the balance of Rs. 6,00,000.",
])
VAKALATNAMA = pdf_bytes("VAKALATNAMA", ["This vakalatnama dated 02 July 2025 authorises the advocate to appear."])
RESPONDENT_VAKALATNAMA = pdf_bytes("VAKALATNAMA", ["This vakalatnama dated 05 August 2025 authorises the advocate to appear."])
WRITTEN = pdf_bytes("WRITTEN STATEMENT", [
    "The defendant denies that the full quantity was delivered.",
    "Only 3,200 metres reached the factory on 12 May 2025.",
])
DRAFT = pdf_bytes("PRIVATE NOTE", ["On 01 January 2020 a private draft note was written by the advocate."])

CASE = {
    "title": "Smoke %s: Rao vs Textile Co" % stamp, "case_type": "Civil · Contract dispute",
    "court": "Civil Court, Chennai", "claimant": "Rao", "respondent": "Textile Co",
    "fee_amount": "", "fee_receipt": "",
}

print("Server:", BASE)
r = client.get("/health")
check("Server is up", r.status_code == 200 and r.json().get("status") == "ok")
check("Safe headers are sent", r.headers.get("x-content-type-options") == "nosniff")
check("No login is refused", client.get("/sync/cases").status_code == 401)
check("A garbage token is refused", client.get("/sync/cases", headers={"Authorization": "Bearer garbage"}).status_code == 401)

reg, _ = login("registry@courtease.in", "Registry#2026")
judge, _ = login("judge1@courtease.in", "Judge#2026")
ana, _ = login("lawyer1@courtease.in", "Lawyer#2026")
vik, _ = login("lawyer2@courtease.in", "Lawyer#2026")
print("-- roles")
check("Lawyer cannot use registry routes", client.get("/registry/report", headers=ana).status_code == 403)
check("Lawyer cannot use judge routes", client.post("/judge/cases/1/review", headers=ana).status_code == 403)
check("Judge cannot file a case", client.post("/cases", headers=judge, json=CASE).status_code == 403)
for who, h in (("registry", reg), ("judge", judge), ("lawyer", ana)):
    check("Upcoming list works for the " + who, client.get("/upcoming", headers=h).status_code == 200)

print("-- signup and approval")
email = "smoke%s@example.com" % stamp
r = client.post("/auth/signup", json={
    "full_name": "Smoke Tester", "email": email, "phone": "9" + stamp[-9:], "role": "lawyer",
    "reg_no": "SM/%s/2026" % stamp, "council": "Bar Council of Tamil Nadu", "password": "Smoke#2026",
})
check("New lawyer can request an account", r.status_code == 201, r.text)
newbie, _ = login(email, "Smoke#2026")
check("A pending account cannot use the app", client.get("/sync/cases", headers=newbie).status_code == 403)
nid = user_id(reg, email)
r = client.post("/registry/accounts/%s/approve" % nid, headers=reg, json={"court": "", "courtroom": ""})
check("Registry approves the lawyer", r.status_code == 200 and r.json().get("status") == "approved", r.text)
check("An approved account works", client.get("/sync/cases", headers=newbie).status_code == 200)

print("-- filing, scrutiny and registration")
r = client.post("/cases", headers=ana, json=CASE)
check("Lawyer creates a draft", r.status_code == 201, r.text)
cid = r.json()["id"]
plaint_id = upload(ana, cid, "Plaint", "plaint.pdf", PLAINT).json().get("id")
upload(ana, cid, "Vakalatnama", "vakalatnama.pdf", VAKALATNAMA)
check("Only the lead can edit (respondent's lawyer refused)", client.put("/cases/%d" % cid, headers=vik, json=CASE).status_code == 403)
check("A draft cannot be registered", client.post("/registry/cases/%d/register" % cid, headers=reg).status_code == 400)
check("Lawyer files the case", client.post("/cases/%d/file" % cid, headers=ana).status_code == 200)
check("Missing court fee fails scrutiny", client.post("/registry/cases/%d/register" % cid, headers=reg).status_code == 400)
r = client.post("/registry/cases/%d/return" % cid, headers=reg, json={"note": "Court fee details are missing"})
check("Registry returns the case", r.status_code == 200 and r.json()["status"] == "returned", r.text)
fixed = dict(CASE, fee_amount="4,000", fee_receipt="RC-5521")
check("Lawyer corrects the details", client.put("/cases/%d" % cid, headers=ana, json=fixed).status_code == 200)
check("Lawyer files again", client.post("/cases/%d/file" % cid, headers=ana).status_code == 200)
r = client.post("/registry/cases/%d/register" % cid, headers=reg)
check("Registry registers the case", r.status_code == 200 and r.json()["case_no"].startswith("OS "), r.text)

print("-- allocation and notice")
jid = user_id(reg, "judge1@courtease.in")
r = client.post("/registry/cases/%d/allocate" % cid, headers=reg, json={"judge_id": jid, "reason": "smoke test"})
check("Registry allocates a judge", r.status_code == 200, r.text)
r = client.post("/registry/cases/%d/notice" % cid, headers=reg)
check("Registry issues the notice", r.status_code == 200 and bool(r.json()["case_code"]), r.text)
code = r.json()["case_code"]

print("-- respondent appears")
join = lambda h: client.post(
    "/cases/join-file", headers=h, data={"code": code, "pages": "1"},
    files={"file": ("vakalatnama.pdf", RESPONDENT_VAKALATNAMA, "application/pdf")},
)
check("The claimant's own lawyer cannot join as respondent", join(ana).status_code == 400)
r = join(vik)
check("Respondent's lawyer joins with the code", r.status_code == 200 and r.json()["status"] == "pleadings", r.text)
check("The case code is hidden from the respondent", r.json().get("case_code") == "")
check("Joining twice is refused", join(vik).status_code == 400)
r = upload(vik, cid, "Written statement", "written_statement.pdf", WRITTEN)
check("Respondent uploads the written statement", r.status_code == 201, r.text)
ws_id = r.json().get("id")
check("Respondent files it", client.post("/docs/%s/file" % ws_id, headers=vik).status_code == 200)
r = upload(ana, cid, "Evidence", "private_note.pdf", DRAFT)
check("Claimant uploads a private draft", r.status_code == 201, r.text)
draft_id = r.json().get("id")

print("-- who sees which documents")
check("The judge does not see the draft", draft_id not in visible_doc_ids(judge))
check("The other side does not see the draft", draft_id not in visible_doc_ids(vik))
check("The owner sees the draft", draft_id in visible_doc_ids(ana))
check("The judge sees the filed plaint", plaint_id in visible_doc_ids(judge))
r = client.get("/docs/%s/content" % plaint_id, headers=judge)
check("The judge opens the filed plaint", r.status_code == 200 and r.content[:4] == b"%PDF")
check("The judge cannot open the draft", client.get("/docs/%s/content" % draft_id, headers=judge).status_code == 404)
check("The registry cannot open any document", client.get("/docs/%s/content" % plaint_id, headers=reg).status_code == 403)
r = client.get("/docs/%s/integrity" % plaint_id, headers=ana)
check("Integrity check verifies the plaint", r.status_code == 200 and r.json().get("status") == "verified", r.text)

print("-- analysis")
state = read_all(ana, cid)
check("All documents are read", state is not None and all(i["status"] == "done" for i in state["items"]), state)
tl = client.get("/cases/%d/timeline" % cid, headers=ana).json()
check("Lawyer's timeline finds dated events", len(tl["events"]) >= 4, len(tl["events"]))
check("Lawyer's timeline includes own draft", any(e["doc_id"] == draft_id for e in tl["events"]))
tlj = client.get("/cases/%d/timeline" % cid, headers=judge).json()
check("Judge's timeline has events", len(tlj["events"]) >= 3, len(tlj["events"]))
check("Judge's timeline has NO draft events", not any(e["doc_id"] == draft_id for e in tlj["events"]))
tlv = client.get("/cases/%d/timeline" % cid, headers=vik).json()
check("Other side's timeline has NO draft events", not any(e["doc_id"] == draft_id for e in tlv["events"]))
for who, h in (("judge", judge), ("other side", vik)):
    iss = client.get("/cases/%d/issues" % cid, headers=h).json()
    fl = client.get("/cases/%d/flags" % cid, headers=h).json()
    bad = [p for i in iss["issues"] for p in i["passages"] if p["doc_id"] == draft_id]
    bad += [p for f in fl["flags"] for p in (f["claimant"] + f["respondent"]) if p["doc_id"] == draft_id]
    check("Issues and flags show no draft to the " + who, not bad)
fl = client.get("/cases/%d/flags" % cid, headers=ana).json()
check("Verification flags are found", len(fl["flags"]) >= 1, len(fl["flags"]))
s = client.get("/cases/%d/summary" % cid, headers=judge).json()
check("Judge's summary says filed documents only", "Filed documents only" in s.get("scope", ""), s)
check("The registry cannot read the analysis", client.get("/cases/%d/timeline" % cid, headers=reg).status_code == 403)

print("-- judge and orders")
check("Judge starts the review", client.post("/judge/cases/%d/review" % cid, headers=judge).status_code == 200)
r = client.post("/judge/cases/%d/orders" % cid, headers=judge, json={
    "kind": "Direction", "title": "File the rejoinder", "body": "The claimant shall file a rejoinder within two weeks.",
})
check("Judge issues a direction", r.status_code == 201, r.text)
order_id = r.json().get("id")
r = client.post("/judge/cases/%d/orders" % cid, headers=judge, json={
    "kind": "Judgment", "title": "Judgment", "body": "The suit is decreed in part, with costs as stated.",
})
check("Judge issues a judgment", r.status_code == 201, r.text)

print("-- PDFs")
for who, h in (("lawyer", ana), ("judge", judge)):
    r = client.get("/cases/%d/report" % cid, headers=h)
    check("The %s gets a PDF report" % who, r.status_code == 200 and r.content[:4] == b"%PDF", r.status_code)
check("The registry cannot get a report", client.get("/cases/%d/report" % cid, headers=reg).status_code == 403)
for who, h in (("registry", reg), ("judge", judge), ("claimant's lawyer", ana)):
    r = client.get("/cases/%d/notice.pdf" % cid, headers=h)
    check("The %s gets the notice PDF" % who, r.status_code == 200 and r.content[:4] == b"%PDF", r.status_code)
for who, h in (("judge", judge), ("claimant's lawyer", ana), ("respondent's lawyer", vik)):
    r = client.get("/orders/%s/pdf" % order_id, headers=h)
    check("The %s gets the order PDF" % who, r.status_code == 200 and r.content[:4] == b"%PDF", r.status_code)
check("The registry cannot get an order PDF", client.get("/orders/%s/pdf" % order_id, headers=reg).status_code == 403)

print("-- closing and history")
r = client.post("/judge/cases/%d/close" % cid, headers=judge, json={"reason": "Disposed after judgment (smoke test)"})
check("Judge closes the case", r.status_code == 200 and r.json()["status"] == "closed", r.text)
check("A closed case accepts no new documents", upload(ana, cid, "Evidence", "late.pdf", DRAFT).status_code == 400)
kinds = {e["kind"] for e in client.get("/cases/%d/history" % cid, headers=ana).json()["events"]}
for kind in ("filed", "returned", "registered", "allocated", "notice", "appeared", "review", "order", "closed"):
    check("History records: " + kind, kind in kinds, sorted(kinds))
jkinds = {e["kind"] for e in client.get("/cases/%d/history" % cid, headers=judge).json()["events"]}
check("History hides 'returned' from the judge", "returned" not in jkinds)
check("History shows the judge the later stages", {"registered", "notice", "closed"} <= jkinds, sorted(jkinds))
actions = " ".join(a["action"] for a in client.get("/registry/audit", headers=reg).json())
check("The audit log recorded the main steps", all(w in actions for w in ("Filed case", "Registered case", "Issued notice", "Closed case")))

print()
print("PASS %d   FAIL %d" % (sum(results), len(results) - sum(results)))
sys.exit(0 if all(results) else 1)