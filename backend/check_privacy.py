import sys

from fastapi import HTTPException
from sqlalchemy import select

import main as m
from database import SessionLocal
from models import Case, User
from summary import endpoint_of, leaks

PATHS = ["/cases/{cid}/timeline", "/cases/{cid}/issues", "/cases/{cid}/flags"]

db = SessionLocal()
problems = 0
checked = 0
users = list(db.scalars(select(User).where(User.status == "approved")))
registry = [u for u in users if u.role == "registry"]

for c in db.scalars(select(Case)):
    viewers = []
    for u in users:
        if u.role == "judge" and c.judge_id == u.id and c.status in m.JUDGE_SEES:
            viewers.append((u, None, "judge"))
        elif u.role == "lawyer":
            side = m.side_of(db, c.id, u.id)
            if side:
                viewers.append((u, side, f"lawyer ({side})"))

    for user, side, label in viewers:
        try:
            tl, iss, fl = [endpoint_of(m.app, p)(cid=c.id, user=user, db=db) for p in PATHS]
        except HTTPException as exc:
            problems += 1
            print(f"FAIL  {m.label(c)} as {user.full_name} [{label}]: refused with {exc.status_code}")
            continue
        checked += 1
        bad = leaks(side, tl, iss, fl)
        if bad:
            problems += 1
            print(f"FAIL  {m.label(c)} as {user.full_name} [{label}]")
            for line in bad:
                print("      ", line)
        else:
            print(f"OK    {m.label(c)} as {user.full_name} [{label}]: "
                  f"{len(tl.events)} events, {len(iss.issues)} issues, {len(fl.flags)} flags")

    for reg in registry:
        try:
            endpoint_of(m.app, PATHS[0])(cid=c.id, user=reg, db=db)
            problems += 1
            print(f"FAIL  {m.label(c)}: the registry was NOT refused")
        except HTTPException as exc:
            if exc.status_code == 403:
                print(f"OK    {m.label(c)}: the registry is refused")
            else:
                problems += 1
                print(f"FAIL  {m.label(c)}: the registry got status {exc.status_code}")

db.close()
print()
print(f"Checked {checked} viewer(s). Problems: {problems}")
sys.exit(1 if problems else 0)