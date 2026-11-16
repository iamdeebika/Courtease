import os
import subprocess
import sys

from fastapi.routing import APIRoute
from sqlalchemy import select

import main as m
from database import SessionLocal
from models import User
from security import SECRET_KEY, verify_secret

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PUBLIC = {"/health", "/auth/signup", "/auth/login", "/auth/reset-password"}
KNOWN_DEMO_PASSWORDS = ["Registry#2026", "Judge#2026", "Lawyer#2026"]

levels = []


def say(level, text):
    levels.append(level)
    print(f"{level:<5} {text}")


def git(*args):
    try:
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    except OSError:
        return None


def dependency_names(dependant):
    names = set()
    for d in dependant.dependencies:
        if d.call is not None:
            names.add(getattr(d.call, "__name__", ""))
        names |= dependency_names(d)
    return names


# ---------- settings ----------
if SECRET_KEY == "dev-secret-change-me":
    say("FAIL", "SECRET_KEY is the built-in default. Anyone could forge a login token.")
elif len(SECRET_KEY) < 32:
    say("WARN", "SECRET_KEY is shorter than 32 characters.")
else:
    say("PASS", "SECRET_KEY is set and long enough.")

try:
    minutes = int(os.getenv("ACCESS_TOKEN_MINUTES", "1440"))
except ValueError:
    minutes = -1
if minutes <= 5:
    say("FAIL", f"ACCESS_TOKEN_MINUTES is {minutes}. That looks like a leftover test value.")
elif minutes > 1440:
    say("WARN", f"ACCESS_TOKEN_MINUTES is {minutes}. Logins last longer than a day.")
else:
    say("PASS", f"Logins last {minutes} minutes.")

if os.getenv("LAW_DEMO") == "1":
    say("FAIL", "LAW_DEMO=1 is on. Sample cards would appear next to real law results.")
else:
    say("PASS", "LAW_DEMO is off.")

if os.getenv("DB_USER", "root").lower() == "root":
    say("WARN", "The app connects to MySQL as root. Use a limited user.")
else:
    say("PASS", "The app connects to MySQL with a limited user.")

if os.getenv("APP_ENV", "").strip().lower() == "production":
    say("PASS", "APP_ENV=production: the API docs page is hidden.")
else:
    say("WARN", "The API docs page (/docs) is visible. Set APP_ENV=production before the final demo.")

# ---------- secrets and Git ----------
ignored = git("check-ignore", "-q", "backend/.env")
tracked = git("ls-files", "--error-unmatch", "backend/.env")
if ignored is None:
    say("WARN", "Git was not found, so .env protection could not be checked.")
else:
    if tracked is not None and tracked.returncode == 0:
        say("FAIL", "backend/.env is tracked by Git. Run: git rm --cached backend/.env")
    elif ignored.returncode == 0:
        say("PASS", "backend/.env is ignored by Git.")
    else:
        say("FAIL", "backend/.env is NOT ignored by Git. Add it to .gitignore.")
    up = git("check-ignore", "-q", "backend/uploads/test.pdf")
    if up is not None and up.returncode == 0:
        say("PASS", "backend/uploads is ignored by Git.")
    else:
        say("FAIL", "backend/uploads is NOT ignored by Git. Add backend/uploads/ to .gitignore.")

# ---------- middleware ----------
names = [getattr(mw, "cls", type(mw)).__name__ for mw in m.app.user_middleware]
say("PASS" if "Guard" in names else "WARN", "Login lockout and safe headers are " + ("installed." if "Guard" in names else "NOT installed."))
if "CORSMiddleware" in names:
    say("WARN", "CORS is enabled. A phone app does not need it.")
else:
    say("PASS", "CORS is not enabled.")
if any(r.__class__.__name__ == "Mount" for r in m.app.routes):
    say("WARN", "A mounted static folder exists. Check it does not expose uploads.")

# ---------- every route needs a login, and the right role ----------
open_routes = []
role_gaps = []
for route in m.app.routes:
    if not isinstance(route, APIRoute) or route.path in PUBLIC:
        continue
    found = dependency_names(route.dependant)
    label = ",".join(sorted(route.methods or [])) + " " + route.path
    if "current_user" not in found:
        open_routes.append(label)
    if route.path.startswith("/registry/") and "registry_user" not in found:
        role_gaps.append(label + " (needs registry_user)")
    if route.path.startswith("/judge/") and "judge_user" not in found:
        role_gaps.append(label + " (needs judge_user)")

if open_routes:
    say("FAIL", f"{len(open_routes)} route(s) do not require a login:")
    for r in open_routes:
        print("        ", r)
else:
    say("PASS", "Every route except health, signup, login and password reset requires a login.")
if role_gaps:
    say("FAIL", f"{len(role_gaps)} route(s) are missing a role check:")
    for r in role_gaps:
        print("        ", r)
else:
    say("PASS", "All /registry and /judge routes check the role.")

# ---------- accounts ----------
db = SessionLocal()
weak = []
for u in db.scalars(select(User)):
    if any(verify_secret(p, u.password_hash) for p in KNOWN_DEMO_PASSWORDS):
        weak.append(u.email)
db.close()
if weak:
    say("WARN", f"{len(weak)} account(s) still use a known demo password (fine for the demo, change before real use):")
    for e in weak:
        print("        ", e)
else:
    say("PASS", "No account uses a known demo password.")

print()
print(f"PASS {levels.count('PASS')} · WARN {levels.count('WARN')} · FAIL {levels.count('FAIL')}")
sys.exit(1 if "FAIL" in levels else 0)