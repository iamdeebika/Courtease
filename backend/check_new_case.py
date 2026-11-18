from sqlalchemy import select

from database import SessionLocal
from models import Case, CaseCounsel, User

db = SessionLocal()
print("Newest users:")
for u in db.scalars(select(User).order_by(User.id.desc()).limit(5)):
    print(" ", u.id, "|", u.full_name, "|", u.email, "|", u.role, "|", u.status)
print()
print("Newest cases:")
for c in db.scalars(select(Case).order_by(Case.id.desc()).limit(5)):
    print(" case", c.id, "|", c.title, "|", c.status)
    for t in db.scalars(select(CaseCounsel).where(CaseCounsel.case_id == c.id).order_by(CaseCounsel.position)):
        u = db.get(User, t.user_id)
        print("     counsel:", t.side, "position", t.position, "|", u.email if u else t.user_id)
db.close()
