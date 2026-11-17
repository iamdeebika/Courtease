import sys
from datetime import datetime

import httpx

BASE = "http://127.0.0.1:8000"
client = httpx.Client(base_url=BASE, timeout=30)
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
    return {"Authorization": "Bearer " + r.json()["access_token"]}


reg = login("registry@courtease.in", "Registry#2026")
ana = login("lawyer1@courtease.in", "Lawyer#2026")
vik = login("lawyer2@courtease.in", "Lawyer#2026")

cases = client.get("/sync/cases", headers=ana).json()["cases"]
if not cases:
    sys.exit("Ananya has no case. Use Reset case data in the registry app first.")
cid = cases[0]["id"]

good = {
    "case_id": cid, "title": "Sample v. Example %s" % stamp, "citation": "(2017) 10 SCC 1",
    "court": "Supreme Court of India", "date": "24 Aug 2017",
    "summary": "A short note in my own words about why this case matters.",
    "source_url": "https://indiankanoon.org/doc/1/", "note": "private test note",
}
made = []

r = client.post("/precedents/manual", headers=ana, json=good)
check("A lawyer saves a typed-in precedent", r.status_code == 201, r.text)
pid = r.json().get("id") if r.status_code == 201 else None
if pid:
    made.append(pid)
    check("It is marked as typed in", r.json().get("kind") == "manual")
    check("The citation is kept", r.json().get("ref") == "(2017) 10 SCC 1")

lst = client.get("/precedents", headers=ana, params={"case_id": cid}).json()
check("It appears in the owner's list", any(p["id"] == pid for p in lst))
other = client.get("/precedents", headers=vik, params={"case_id": cid}).json()
check("The other lawyer does not see it", not any(p["id"] == pid for p in other))
check("Saving it twice is refused", client.post("/precedents/manual", headers=ana, json=good).status_code == 400)
check("A bad link is refused", client.post(
    "/precedents/manual", headers=ana, json=dict(good, title="Bad link %s" % stamp, source_url="hello world")
).status_code == 422)
check("A too short case name is refused", client.post(
    "/precedents/manual", headers=ana, json=dict(good, title="ab")).status_code == 422)
check("A too long citation is refused", client.post(
    "/precedents/manual", headers=ana, json=dict(good, title="Long %s" % stamp, citation="x" * 81)
).status_code == 422)

r = client.post("/precedents/manual", headers=ana, json=dict(
    good, title="No scheme %s" % stamp, source_url="indiankanoon.org/doc/2/"))
check("A link without https gets it added", r.status_code == 201 and r.json().get("source_url", "").startswith("https://"), r.text)
if r.status_code == 201:
    made.append(r.json()["id"])

check("An unknown case is refused", client.post(
    "/precedents/manual", headers=ana, json=dict(good, case_id=999999, title="Ghost %s" % stamp)
).status_code == 404)
check("The registry is refused", client.post("/precedents/manual", headers=reg, json=good).status_code == 403)
check("No login is refused", client.post("/precedents/manual", json=good).status_code == 401)

for item in made:
    client.delete("/precedents/%d" % item, headers=ana)
after = client.get("/precedents", headers=ana, params={"case_id": cid}).json()
check("The test entries were removed", not any(p["id"] in made for p in after))

print()
print("PASS %d   FAIL %d" % (sum(results), len(results) - sum(results)))
sys.exit(0 if all(results) else 1)