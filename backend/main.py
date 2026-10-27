import time

from fastapi import Depends, FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import Base, engine, get_db
from models import User
from schemas import LoginIn, MessageOut, ResetPasswordIn, SignupIn, TokenOut, UserOut
from security import create_token, decode_token, hash_secret, verify_secret

Base.metadata.create_all(bind=engine)

app = FastAPI(title="CourtEase API", version="1.1")
bearer = HTTPBearer(auto_error=False)

# Simple limit on reset attempts: 5 wrong tries per email in 10 minutes.
_reset_failures: dict = {}


def _too_many_attempts(email: str) -> bool:
    now = time.time()
    recent = [t for t in _reset_failures.get(email, []) if now - t < 600]
    _reset_failures[email] = recent
    return len(recent) >= 5


@app.exception_handler(RequestValidationError)
async def validation_handler(request, exc: RequestValidationError):
    first = exc.errors()[0]
    message = str(first.get("msg", "Invalid input")).replace("Value error, ", "")
    return JSONResponse(status_code=422, content={"detail": message})


def current_user(
    creds: HTTPAuthorizationCredentials = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if creds is None:
        raise HTTPException(status_code=401, detail="Not signed in")
    user_id = decode_token(creds.credentials)
    user = db.get(User, user_id) if user_id else None
    if user is None:
        raise HTTPException(status_code=401, detail="Session expired")
    return user


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/auth/signup", response_model=UserOut, status_code=201)
def signup(body: SignupIn, db: Session = Depends(get_db)):
    email = body.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status_code=409, detail="This email is already registered")
    user = User(
        full_name=body.full_name,
        email=email,
        phone=body.phone,
        role=body.role,
        password_hash=hash_secret(body.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@app.post("/auth/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    if user is None or not verify_secret(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Wrong email or password")
    return TokenOut(access_token=create_token(user.id), user=UserOut.model_validate(user))


@app.post("/auth/reset-password", response_model=MessageOut)
def reset_password(body: ResetPasswordIn, db: Session = Depends(get_db)):
    email = body.email.lower()
    if _too_many_attempts(email):
        raise HTTPException(status_code=429, detail="Too many attempts. Try again in 10 minutes")

    user = db.scalar(select(User).where(User.email == email))
    if user is None or user.phone != body.phone:
        _reset_failures.setdefault(email, []).append(time.time())
        raise HTTPException(status_code=400, detail="Email and phone don't match our records")

    user.password_hash = hash_secret(body.new_password)
    db.commit()
    _reset_failures.pop(email, None)
    return MessageOut(message="Password updated")


@app.get("/users/me", response_model=UserOut)
def me(user: User = Depends(current_user)):
    return user