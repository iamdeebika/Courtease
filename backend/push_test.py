"""Push notification checks.

  python push_test.py check                      checks the Firebase key without needing a phone
  python push_test.py user@example.com           shows how many phones are registered for that user
  python push_test.py user@example.com send      sends a test push to those phones
"""
import sys

from sqlalchemy import func, select

import push
from database import Base, SessionLocal, engine
from models import User

Base.metadata.create_all(bind=engine)
args = sys.argv[1:]

if args == ["check"]:
    app = push.firebase()
    if app is None:
        sys.exit("The Firebase key could not be loaded. See the message above.")
    from firebase_admin import messaging

    message = messaging.Message(
        token="x" * 150,
        notification=messaging.Notification(title="check", body="check"),
    )
    try:
        messaging.send(message, dry_run=True, app=app)
        print("Unexpected: Google accepted a fake token.")
    except Exception as exc:
        name = type(exc).__name__
        print("Result:", name)
        print(str(exc)[:240])
        if name in ("InvalidArgumentError", "UnregisteredError"):
            print("GOOD: your key and the Cloud Messaging API work. Google only rejected the fake token.")
        else:
            print("PROBLEM: see the table in the instructions for this result.")
    sys.exit(0)

if not args:
    print(__doc__)
    sys.exit(0)

db = SessionLocal()
user = db.scalar(select(User).where(func.lower(User.email) == args[0].lower()))
if user is None:
    sys.exit("No such user: " + args[0])
count = db.scalar(select(func.count()).select_from(push.DeviceToken).where(push.DeviceToken.user_id == user.id))
print(f"{user.full_name}: {count} phone(s) registered.")
db.close()
if len(args) > 1 and args[1] == "send":
    if count == 0:
        sys.exit("No phone is registered yet. Sign in on the phone first and wait about 10 seconds.")
    sent = push.send_to_user(user.id, 0)
    print(f"Sent to {sent} phone(s).")
