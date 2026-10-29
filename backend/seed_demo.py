from database import Base, SessionLocal, engine
from models import User
from security import hash_secret

Base.metadata.create_all(bind=engine)

DEMO = [
    dict(id=1, full_name="Court Registry", email="registry@courtease.in", phone="9000000001", role="registry",
         status="approved", registry_id="REG-0001", password="Registry#2026"),
    dict(id=2, full_name="Justice R. Meenakshi", email="judge1@courtease.in", phone="9000000002", role="judge",
         status="approved", reg_no="TN-EMP-1201", council="Civil Court, Chennai", registry_id="JDG-2026-0001",
         court="Civil Court, Chennai", courtroom="Court Hall 1", password="Judge#2026"),
    dict(id=3, full_name="Justice A. Karthik", email="judge2@courtease.in", phone="9000000003", role="judge",
         status="approved", reg_no="TN-EMP-1302", council="District Court, Madurai", registry_id="JDG-2026-0002",
         court="District Court, Madurai", courtroom="Court Hall 2", password="Judge#2026"),
    dict(id=4, full_name="Ananya Rao", email="lawyer1@courtease.in", phone="9000000004", role="lawyer",
         status="approved", reg_no="TN/1234/2015", council="Bar Council of Tamil Nadu",
         registry_id="ADV-2026-0001", password="Lawyer#2026"),
    dict(id=5, full_name="Vikram Iyer", email="lawyer2@courtease.in", phone="9000000005", role="lawyer",
         status="approved", reg_no="TN/5678/2012", council="Bar Council of Tamil Nadu",
         registry_id="ADV-2026-0002", password="Lawyer#2026"),
    dict(id=6, full_name="Divya Nair", email="divya@courtease.in", phone="9000000006", role="lawyer",
         status="pending", reg_no="KA/9012/2018", council="Bar Council of Karnataka", password="Lawyer#2026"),
    dict(id=7, full_name="Justice S. Prakash", email="judge3@courtease.in", phone="9000000007", role="judge",
         status="pending", reg_no="TN-EMP-1455", council="Civil Court, Chennai", password="Judge#2026"),
]

db = SessionLocal()
added = 0
for row in DEMO:
    if db.get(User, row["id"]) is not None:
        continue
    data = dict(row)
    password = data.pop("password")
    db.add(User(**data, password_hash=hash_secret(password)))
    added += 1
db.commit()
db.close()
print(f"Seeded {added} demo account(s).")