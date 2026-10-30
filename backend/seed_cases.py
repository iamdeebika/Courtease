from datetime import date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from database import Base, SessionLocal, engine
from models import Case, CaseCounsel, Document, Notification, User, utcnow


def seed_cases(db: Session) -> None:
    now = utcnow()
    year = datetime.now().year
    needed = {1, 3, 4, 5}
    have = {u.id for u in db.scalars(select(User).where(User.id.in_(needed)))}
    if have != needed:
        print("Run seed_demo.py first: the demo accounts are missing.")
        return

    c1 = Case(
        title="Ravi vs Kumar Traders", case_type="Civil · Contract dispute", court="Civil Court, Chennai",
        claimant="Ravi", respondent="Kumar Traders", status="filed",
        fee_amount="5,000", fee_receipt="RC-88231", filed_at=now - timedelta(days=1),
    )
    c2 = Case(
        title="Meena vs Sundaram", case_type="Civil · Property dispute", court="District Court, Madurai",
        claimant="Meena", respondent="Sundaram", status="notice", case_no=f"OS 7/{year}",
        fee_amount="3,500", fee_receipt="RC-77120", judge_id=3, courtroom="Court Hall 2",
        case_code="CE-7K2P9", response_due=date.today() + timedelta(days=30),
        filed_at=now - timedelta(days=6),
    )
    c3 = Case(
        title="Arun vs Greenfield Builders", case_type="Civil · Recovery suit", court="Civil Court, Chennai",
        claimant="Arun", respondent="Greenfield Builders", status="filed", filed_at=now - timedelta(days=2),
    )
    db.add_all([c1, c2, c3])
    db.flush()

    db.add_all([
        CaseCounsel(case_id=c1.id, user_id=4, side="claimant", position=0),
        CaseCounsel(case_id=c2.id, user_id=4, side="claimant", position=0),
        CaseCounsel(case_id=c3.id, user_id=5, side="claimant", position=0),
    ])

    def doc(case, name, kind, user, pages, days):
        return Document(
            case_id=case.id, name=name, doc_type=kind, side="claimant", filed=True,
            uploaded_by=user, pages=pages, created_at=now - timedelta(days=days),
        )

    db.add_all([
        doc(c1, "Plaint - Ravi vs Kumar Traders.pdf", "Plaint", 4, 24, 1),
        doc(c1, "Vakalatnama.pdf", "Vakalatnama", 4, 2, 1),
        doc(c2, "Plaint - Meena vs Sundaram.pdf", "Plaint", 4, 18, 6),
        doc(c2, "Vakalatnama.pdf", "Vakalatnama", 4, 2, 6),
        doc(c3, "Plaint - Arun vs Greenfield.pdf", "Plaint", 5, 12, 2),
    ])

    db.add_all([
        Notification(user_id=1, title="New filing", body="Ravi vs Kumar Traders · filed for scrutiny", case_id=c1.id),
        Notification(user_id=1, title="New filing", body="Arun vs Greenfield Builders · filed for scrutiny", case_id=c3.id),
        Notification(user_id=3, title="New case allocated", body=f"OS 7/{year} · Meena vs Sundaram", case_id=c2.id),
        Notification(user_id=4, title="Notice issued", body=f"OS 7/{year}: case code CE-7K2P9", case_id=c2.id),
    ])
    db.commit()
    print("Seeded 3 demo cases.")


if __name__ == "__main__":
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    if session.scalar(select(Case.id).limit(1)) is not None:
        print("Cases already exist. Use 'Reset case data' in the registry app to start over.")
    else:
        seed_cases(session)
    session.close()