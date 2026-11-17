from sqlalchemy import select

from database import SessionLocal
from models import Notification
from push_rules import is_important

db = SessionLocal()
seen = {}
for n in db.scalars(select(Notification).order_by(Notification.id.desc()).limit(1000)):
    if n.title not in seen:
        seen[n.title] = [is_important(n.title, n.body), 0, n.body]
    seen[n.title][1] += 1
db.close()

print("Verdict".ljust(13), "Count".rjust(5), "  Title  |  text")
for title, (push, count, body) in sorted(seen.items(), key=lambda kv: (not kv[1][0], kv[0])):
    print(("push" if push else "in-app only").ljust(13), str(count).rjust(5), " ", title, " | ", (body or "")[:70])
print()
print("Check the text column: it must not contain party names or private details, because it now shows on the lock screen.")
