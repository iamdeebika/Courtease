import sys
import time

import httpx
from sqlalchemy import select

from database import SessionLocal
from models import Case, Document, Order, User

BASE = "http://127.0.0.1:8000"
client = httpx.Client(base_url=BASE, timeout=60)
results = []
stamp = time.strftime("%m%d%H%M%S")
REFUSED = (401, 403, 404)


def check(name, ok, detail=""):
    results.append(bool(ok))
    line = ("PASS  " if ok else "FAIL  ") + name
    if not ok and detail != "":
        line += "   -> " + str(detail)[:240]
    print(line)


def login(email, password):
    r = client.post("/auth/login", json={"email": email, "password": password})
    if r.status_code != 200:
        sys.exit("Could not sign in as %s: %s %s" % (email, r.status_code, r.text[:200]))
    return {"Authorization": "Bearer " + r.json()["access_token"]}


def sweep(headers, cases, docs, orders):
    """Calls every case, document and order route and lists the ones that were NOT refused."""
    bad = []
    tried = 0
    for cid in cases:
        for name in ("summary", "timeline", "issues", "flags", "report", "papers", "history", "reading", "notice.pdf"):
            r = client.get("/cases/%d/%s" % (cid, name), headers=headers)
            tried += 1
            if r.status_code not in REFUSED:
                bad.append("GET /cases/%d/%s -> %d" % (cid, name, r.status_code))
        r = client.post("/cases/%d/reading" % cid, headers=headers)
        tried += 1
        if r.status_code not in REFUSED:
            bad.append("POST /cases/%d/reading -> %d" % (cid, r.status_code))
    for did in docs:
        for name in ("content", "integrity", "pages"):
            r = client.get("/docs/%d/%s" % (did, name), headers=headers)
            tried += 1
            if r.status_code not in REFUSED:
                bad.append("GET /docs/%d/%s -> %d" % (did, name, r.status_code))
    for oid in orders:
        r = client.get("/orders/%d/pdf" % oid, headers=headers)
        tried += 1
        if r.status_code not in REFUSED:
            bad.append("GET /orders/%d/pdf -> %d" % (oid, r.status_code))
    return tried, bad


reg = login("registry@courtease.in", "Registry#2026")
judge2 = login("judge2@courtease.in", "Judge#2026")

db = SessionLocal()
j2 = db.scalar(select(User).where(User.email == "judge2@courtease.in"))
cases = list(db.scalars(select(Case).order_by(Case.id.desc()).limit(6)))
if not cases:
    sys.exit("There are no cases to test against.")
case_ids = [c.id for c in cases]
doc_ids = [d.id for d in db.scalars(select(Document).where(Document.case_id.in_(case_ids), Document.stored_name != ""))]
order_ids = [o.id for o in db.scalars(select(Order).where(Order.case_id.in_(case_ids)))]
others = [c.id for c in cases if c.judge_id != j2.id]
other_docs = [d.id for d in db.scalars(select(Document).where(Document.case_id.in_(others), Document.stored_name != ""))] if others else []
other_orders = [o.id for o in db.scalars(select(Order).where(Order.case_id.in_(others)))] if others else []
db.close()

email = "outsider%s@example.com" % stamp
r = client.post("/auth/signup", json={
    "full_name": "Outsider Tester", "email": email, "phone": "9" + stamp[-9:], "role": "lawyer",
    "reg_no": "OUT/%s/2026" % stamp, "council": "Bar Council of Tamil Nadu", "password": "Outsider#2026",
})
if r.status_code != 201:
    sys.exit("Could not create the test lawyer: %s %s" % (r.status_code, r.text[:200]))
uid = next((u["id"] for u in client.get("/sync/users", headers=reg).json() if u["email"] == email), None)
r = client.post("/registry/accounts/%s/approve" % uid, headers=reg, json={"court": "", "courtroom": ""})
check("The test lawyer is approved", r.status_code == 200, r.text)
outsider = login(email, "Outsider#2026")

tried, bad = sweep(outsider, case_ids, doc_ids, order_ids)
check("A lawyer on none of the cases is refused everywhere (%d requests)" % tried, not bad, "; ".join(bad[:4]))

mine = client.get("/sync/cases", headers=outsider).json()
check("That lawyer's own case list is empty", not mine.get("cases") and not mine.get("docs"), mine)

if others:
    tried, bad = sweep(judge2, others, other_docs, other_orders)
    check("A judge is refused on cases allocated to someone else (%d requests)" % tried, not bad, "; ".join(bad[:4]))
    seen = [c["id"] for c in client.get("/sync/cases", headers=judge2).json().get("cases", []) if c["id"] in others]
    check("That judge's case list holds only his own cases", not seen, seen)
else:
    print("NOTE  Every recent case is allocated to judge2, so the judge check was skipped.")

for path in ("/cases/%d/summary" % case_ids[0], "/cases/%d/timeline" % case_ids[0], "/cases/%d/report" % case_ids[0]):
    check("No login is refused: " + path, client.get(path).status_code == 401)
if doc_ids:
    check("No login is refused: a document", client.get("/docs/%d/content" % doc_ids[0]).status_code == 401)

print()
print("PASS %d   FAIL %d" % (sum(results), len(results) - sum(results)))
print("Suspend the account %s in the registry Directory afterwards." % email)
sys.exit(0 if all(results) else 1)
