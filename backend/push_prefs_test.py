import sys
import time

import httpx
from sqlalchemy import delete, select

import push
from database import Base, SessionLocal, engine
from models import Notification, User
from push_prefs import wants

Base.metadata.create_all(bind=engine)
BASE = "http://127.0.0.1:8000"
client = httpx.Client(base_url=BASE, timeout=30)
results = []


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


ana = login("lawyer1@courtease.in", "Lawyer#2026")
judge = login("judge1@courtease.in", "Judge#2026")
reg = login("registry@courtease.in", "Registry#2026")

db = SessionLocal()
uid = db.scalar(select(User.id).where(User.email == "lawyer1@courtease.in"))
made = []

check("No login is refused", client.get("/push/settings").status_code == 401)

r = client.get("/push/settings", headers=ana)
keys = [o["key"] for o in r.json()["options"]] if r.status_code == 200 else []
check("A lawyer gets the settings", r.status_code == 200 and "orders" in keys and "team" in keys, r.text)
check("Everything is on by default", r.json()["enabled"] and all(o["enabled"] for o in r.json()["options"]))
jk = [o["key"] for o in client.get("/push/settings", headers=judge).json()["options"]]
check("A judge does not see 'Team and tasks'", "team" not in jk and "orders" in jk, jk)
rk = [o["key"] for o in client.get("/push/settings", headers=reg).json()["options"]]
check("The registry does not see 'Orders and judgments'", "orders" not in rk and "filing" in rk, rk)

r = client.put("/push/settings", headers=ana, json={"enabled": True, "muted": ["orders", "nonsense"]})
check("Muting 'orders' is saved", r.status_code == 200 and not [o for o in r.json()["options"] if o["key"] == "orders"][0]["enabled"], r.text)
check("An unknown option is ignored", all(o["key"] != "nonsense" for o in r.json()["options"]))
again = client.get("/push/settings", headers=ana).json()
check("The choice is still there on the next read", not [o for o in again["options"] if o["key"] == "orders"][0]["enabled"])

with engine.connect() as conn:
    check("An order alert is NOT pushed", wants(conn, uid, "Direction issued", "A direction was issued") is False)
    check("A case alert IS pushed", wants(conn, uid, "Case registered", "Your case was registered") is True)

client.put("/push/settings", headers=ana, json={"enabled": False, "muted": []})
with engine.connect() as conn:
    check("The master switch stops everything", wants(conn, uid, "Case registered", "Your case was registered") is False)

client.put("/push/settings", headers=ana, json={"enabled": True, "muted": ["orders"]})
print("Creating two test alerts for Ananya (the order one should be skipped, the case one pushed)...")
for title, body in (("Direction issued", "Test: a direction was issued"), ("Case registered", "Test: your case was registered")):
    n = Notification(user_id=uid, title=title, body=body, case_id=0)
    db.add(n)
    db.commit()
    made.append(n.id)
time.sleep(6)

db.execute(delete(Notification).where(Notification.id.in_(made)))
db.commit()
db.close()
client.put("/push/settings", headers=ana, json={"enabled": True, "muted": []})
check("Settings are back to everything on", all(o["enabled"] for o in client.get("/push/settings", headers=ana).json()["options"]))

print()
print("PASS %d   FAIL %d" % (sum(results), len(results) - sum(results)))
sys.exit(0 if all(results) else 1)
