from database import Base, SessionLocal, engine
from models import User
from security import hash_secret
from sqlalchemy import func, select

Base.metadata.create_all(bind=engine)

name = input("Full name: ").strip()
email = input("Email: ").strip().lower()
phone = input("Phone (10+ digits): ").strip()
password = input("Password (8+ characters, letters and a number): ")

db = SessionLocal()
if db.scalar(select(User).where(User.email == email)):
    raise SystemExit("That email is already registered.")
count = db.scalar(select(func.count()).select_from(User).where(User.role == "registry")) or 0
user = User(
    full_name=name, email=email, phone=phone, role="registry", status="approved",
    registry_id=f"REG-{count + 1:04d}", password_hash=hash_secret(password),
)
db.add(user)
db.commit()
print("Created registry account:", user.registry_id)