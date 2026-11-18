import sys
import time

import httpx
from sqlalchemy import func, select

from database import SessionLocal
from law import SavedPrecedent
from models import Notification, User
from push import DeviceToken

BASE = "http://127.0.0.1:8000"
client = httpx.Client(base_url=BASE, timeout=60)
results = []
stamp = time.strftime("%m%d%H%M%S")
PW = "Delete#2026"


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


def make_lawyer(tag, phone_start, reg):
    email = ("%s%s@example.com" % (tag, stamp)).lower()
    r = client.post("/auth/signup", json={
        "full_name": "Delete Tester " + tag, "email": email, "phone": phone_start + stamp[-9:], "role": "lawyer",
        "reg_no": "DEL/%s/%s" % (tag, stamp), "council": "Bar Council of Tamil Nadu", "password": PW,
    })
    if r.status_code != 201:
        sys.exit("Could not create %s: %s %s" % (tag, r.status_code, r.text[:200]))
    uid = next((u["id"] for u in client.get("/sync/users", headers=reg).json() if u["email"] == email), None)
    r = client.post("/registry/accounts/%s/approve" % uid, headers=reg, json={"court": "", "courtroom": ""})
    if r.status_code != 200:
        sys.exit("Could not approve %s: %s" % (tag, r.text[:200]))
    return email, uid


reg = login("registry@courtease.in", "Registry#2026")
email_a, uid_a = make_lawyer("delA", "9", reg)
email_b, uid_b = make_lawyer("delB", "8", reg)
a = login(email_a, PW)
b = login(email_b, PW)

r = client.post("/cases", headers=a, json={
    "title": "Delete test %s" % stamp, "case_type": "Civil \u00b7 Contract dispute", "court": "Civil Court, Chennai",
    "claimant": "A", "respondent": "B", "fee_amount": "", "fee_receipt": "",
})
check("Lawyer A created a draft case", r.status_code == 201, r.text)
r = client.post("/auth/delete-account", headers=a, json={"password": PW})
check("A lawyer who leads an open case cannot delete the account", r.status_code == 400 and "open case" in r.text.lower(), r.text)
check("That lawyer can still sign in", client.post("/auth/login", json={"email": email_a, "password": PW}).status_code == 200)

r = client.post("/devices/register", headers=b, json={"token": "t" * 40})
check("Lawyer B registered a phone", r.status_code == 200, r.text)
r = client.post("/precedents/manual", headers=b, json={"case_id": 0, "title": "Delete test precedent"})
check("Lawyer B saved a precedent", r.status_code == 201, r.text)

check("No login is refused", client.post("/auth/delete-account", json={"password": PW}).status_code == 401)
r = client.post("/auth/delete-account", headers=reg, json={"password": "wrong"})
check("The registry account cannot be deleted", r.status_code == 400, r.text)
r = client.post("/auth/delete-account", headers=b, json={"password": "wrong-password"})
check("A wrong password is refused", r.status_code == 400 and "password" in r.text.lower(), r.text)

r = client.post("/auth/delete-account", headers=b, json={"password": PW})
check("Lawyer B deletes the account", r.status_code == 200, r.text)
r = client.post("/auth/login", json={"email": email_b, "password": PW})
check("The old login no longer works", r.status_code in (400, 401, 403), r.status_code)

db = SessionLocal()
u = db.get(User, uid_b)
check("The name, email and phone were replaced", u.full_name == "Deleted user" and u.email.startswith("deleted-") and u.email != email_b and u.phone != "" and u.reg_no == "", "%s | %s" % (u.full_name, u.email))
check("The device token was deleted", db.scalar(select(func.count()).select_from(DeviceToken).where(DeviceToken.user_id == uid_b)) == 0)
check("The saved precedent was deleted", db.scalar(select(func.count()).select_from(SavedPrecedent).where(SavedPrecedent.user_id == uid_b)) == 0)
check("The alerts were deleted", db.scalar(select(func.count()).select_from(Notification).where(Notification.user_id == uid_b)) == 0)
db.close()
check("The old email is gone from the registry's list", not any(x["email"] == email_b for x in client.get("/sync/users", headers=reg).json()))

print()
print("PASS %d   FAIL %d" % (sum(results), len(results) - sum(results)))
print("Lawyer A (%s) still leads a draft. Use Reset case data in the registry app, or leave it." % email_a)
sys.exit(0 if all(results) else 1)

