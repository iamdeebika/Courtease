"""Manual push test.

  python push_manual.py EMAIL direct "Title" "Text"   sends this exact text to the phone
  python push_manual.py EMAIL alert  "Title" "Text"   creates a real alert; it pushes only if the rules say it is important
"""
import sys
import time

from sqlalchemy import delete, func, select

import push
from database import Base, SessionLocal, engine
from models import Notification, User
from push_rules import is_important

Base.metadata.create_all(bind=engine)
args = sys.argv[1:]
if len(args) < 4 or args[1] not in ("direct", "alert"):
    print(__doc__)
    sys.exit(0)

email, mode, title, body = args[0], args[1], args[2], args[3]
db = SessionLocal()
user = db.scalar(select(User).where(func.lower(User.email) == email.lower()))
if user is None:
    sys.exit("No such user: " + email)
count = db.scalar(select(func.count()).select_from(push.DeviceToken).where(push.DeviceToken.user_id == user.id))
print(f"{user.full_name}: {count} phone(s) registered.")
if count == 0:
    sys.exit("No phone is registered. Sign in on the phone, allow notifications, and wait about 15 seconds.")

if mode == "direct":
    sent = push.send_to_user(user.id, 0, title, body)
    print(f"Sent to {sent} phone(s).")
else:
    print("The rules say:", "PUSH" if is_important(title, body) else "in-app only (no push)")
    n = Notification(user_id=user.id, title=title, body=body, case_id=0)
    db.add(n)
    db.commit()
    made = n.id
    print("Alert created. Waiting 6 seconds for the push...")
    time.sleep(6)
    db.execute(delete(Notification).where(Notification.id == made))
    db.commit()
    print("The test alert was removed again.")
db.close()
