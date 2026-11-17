import os
import threading
import time
from datetime import datetime

from fastapi import Depends
from pydantic import BaseModel, field_validator
from sqlalchemy import DateTime, Integer, String, delete, event, select
from sqlalchemy.orm import Mapped, Session, mapped_column, object_session

from database import Base, SessionLocal, get_db
from models import Notification, User, utcnow
from push_rules import is_important

KEY_PATH = os.getenv("FIREBASE_KEY", os.path.join(os.path.dirname(os.path.abspath(__file__)), "firebase-key.json"))
KEY_MAX_DAYS = 30
CHANNEL = "courtease_alerts"
PUSH_TITLE = "CourtEase"
PUSH_BODY = "You have a new alert. Open the app to read it."
MAX_TITLE = 60
MAX_BODY = 140
MAX_PER_USER = 5

_lock = threading.Lock()
_state = {"tried": False, "app": None}


class DeviceToken(Base):
    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    token: Mapped[str] = mapped_column(String(300), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class TokenIn(BaseModel):
    token: str

    @field_validator("token")
    @classmethod
    def _token(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 20 or len(v) > 300 or " " in v:
            raise ValueError("Invalid device token")
        return v


def firebase():
    """Starts the Firebase connection once. Returns None when push is not available."""
    with _lock:
        if _state["tried"]:
            return _state["app"]
        _state["tried"] = True
        if not os.path.isfile(KEY_PATH):
            print("Push is off: firebase-key.json was not found in the backend folder.")
            return None
        age = (time.time() - os.path.getmtime(KEY_PATH)) / 86400
        if age >= KEY_MAX_DAYS:
            print(f"WARNING: the Firebase key is {int(age)} days old. Replace it with a new one.")
        try:
            import firebase_admin
            from firebase_admin import credentials
            _state["app"] = firebase_admin.initialize_app(credentials.Certificate(KEY_PATH))
            print("Push is ready.")
        except Exception as exc:
            print("Push is off:", type(exc).__name__, str(exc)[:160])
        return _state["app"]


def _clip(text, limit):
    text = " ".join((text or "").split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def send_to_user(user_id: int, case_id: int = 0, title=None, body=None) -> int:
    """Sends one push with this title and text to every phone registered for the user. Returns how many were sent."""
    app = firebase()
    if app is None:
        return 0
    from firebase_admin import messaging

    shown_title = _clip(title, MAX_TITLE) or PUSH_TITLE
    shown_body = _clip(body, MAX_BODY) or PUSH_BODY
    db = SessionLocal()
    sent = 0
    try:
        tokens = [t.token for t in db.scalars(select(DeviceToken).where(DeviceToken.user_id == user_id))]
        for token in tokens:
            message = messaging.Message(
                token=token,
                notification=messaging.Notification(title=shown_title, body=shown_body),
                data={"case_id": str(case_id)},
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(channel_id=CHANNEL, visibility="private"),
                ),
            )
            try:
                messaging.send(message, app=app)
                sent += 1
            except (messaging.UnregisteredError, messaging.SenderIdMismatchError):
                db.execute(delete(DeviceToken).where(DeviceToken.token == token))
                db.commit()
                print("Push: removed a token that Google no longer accepts.")
            except Exception as exc:
                print("Push failed:", type(exc).__name__, str(exc)[:160])
    finally:
        db.close()
    return sent


def _send_all(queue):
    counts = {}
    for user_id, case_id, title, body in queue:
        counts[user_id] = counts.get(user_id, 0) + 1
        if counts[user_id] > MAX_PER_USER:
            continue
        try:
            sent = send_to_user(user_id, case_id, title, body)
            print(f"Push: user {user_id}, {sent} device(s): {title}")
        except Exception as exc:
            print("Push error:", type(exc).__name__, str(exc)[:160])


@event.listens_for(Notification, "after_insert")
def _queue(mapper, connection, target):
    """Remembers who just got an important alert. The push is sent only after the database commit succeeds."""
    try:
        if not is_important(target.title, target.body):
            print(f"Push skipped (in-app only): {target.title}")
            return
    except Exception as exc:
        print("Push rule check failed, sending anyway:", type(exc).__name__, str(exc)[:120])
    session = object_session(target)
    if session is not None:
        session.info.setdefault("push_queue", []).append((target.user_id, target.case_id, target.title, target.body))


@event.listens_for(Session, "after_commit")
def _after_commit(session):
    queue = session.info.pop("push_queue", None)
    if queue:
        threading.Thread(target=_send_all, args=(queue,), daemon=True).start()


@event.listens_for(Session, "after_rollback")
def _after_rollback(session):
    session.info.pop("push_queue", None)


def install_push(app, m):
    """Adds the device routes. 'm' is main.py, which owns the shared helpers."""

    @app.post("/devices/register", response_model=m.MessageOut)
    def register(body: TokenIn, user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        row = db.scalar(select(DeviceToken).where(DeviceToken.token == body.token))
        if row is None:
            db.add(DeviceToken(user_id=user.id, token=body.token))
        else:
            row.user_id = user.id
            row.updated_at = utcnow()
        db.commit()
        return m.MessageOut(message="Registered")

    @app.post("/devices/unregister", response_model=m.MessageOut)
    def unregister(body: TokenIn, db: Session = Depends(get_db)):
        db.execute(delete(DeviceToken).where(DeviceToken.token == body.token))
        db.commit()
        return m.MessageOut(message="Removed")
