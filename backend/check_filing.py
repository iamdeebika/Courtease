import httpx
from sqlalchemy import select

from database import SessionLocal
from models import AuditLog, Case, CaseCounsel, User

BASE = "http://127.0.0.1:8000"
db = SessionLocal()

print("1. The server's database, newest cases:")
for c in db.scalars(select(Case).order_by(Case.id.desc()).limit(5)):
    team = []
    for t in db.scalars(select(CaseCounsel).where(CaseCounsel.case_id == c.id).order_by(CaseCounsel.position)):
        u = db.get(User, t.user_id)
        team.append("%s %s" % (t.side, u.email if u else t.user_id))
    print("   case", c.id, "|", c.title, "| status:", c.status, "|", "; ".join(team))

print()
print("2. The newest audit entries:")
for a in db.scalars(select(AuditLog).order_by(AuditLog.id.desc()).limit(6)):
    print("  ", a.created_at, "|", a.action, "|", a.detail)
db.close()

print()
print("3. What the registry's phone receives from the server:")
r = httpx.post(BASE + "/auth/login", json={"email": "registry@courtease.in", "password": "Registry#2026"}, timeout=20)
if r.status_code != 200:
    print("   Could not sign in as the registry:", r.status_code, r.text[:120])
else:
    h = {"Authorization": "Bearer " + r.json()["access_token"]}
    cases = httpx.get(BASE + "/sync/cases", headers=h, timeout=20).json().get("cases", [])
    print("   total cases visible to the registry:", len(cases))
    for c in cases[-5:]:
        print("    case", c.get("id"), "|", c.get("title"), "| status:", c.get("status"))
    waiting = [c for c in cases if str(c.get("status", "")).lower() == "filed"]
    print("   waiting in the filing queue:", len(waiting))
