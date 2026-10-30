from datetime import date, datetime, timezone
from typing import Optional

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from database import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    full_name: Mapped[str] = mapped_column(String(120))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    phone: Mapped[str] = mapped_column(String(20))
    role: Mapped[str] = mapped_column(String(20))
    status: Mapped[str] = mapped_column(String(20), default="pending")
    reg_no: Mapped[str] = mapped_column(String(60), default="")
    council: Mapped[str] = mapped_column(String(120), default="")
    registry_id: Mapped[Optional[str]] = mapped_column(String(30), unique=True, nullable=True)
    court: Mapped[str] = mapped_column(String(120), default="")
    courtroom: Mapped[str] = mapped_column(String(60), default="")
    reject_reason: Mapped[str] = mapped_column(String(255), default="")
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Notification(Base):
    __tablename__ = "notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    title: Mapped[str] = mapped_column(String(160))
    body: Mapped[str] = mapped_column(String(255))
    case_id: Mapped[int] = mapped_column(Integer, default=0)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class AuditLog(Base):
    __tablename__ = "audit_log"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(Integer, nullable=True, index=True)
    action: Mapped[str] = mapped_column(String(80))
    detail: Mapped[str] = mapped_column(String(255), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Case(Base):
    __tablename__ = "cases"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_no: Mapped[Optional[str]] = mapped_column(String(30), unique=True, nullable=True)
    title: Mapped[str] = mapped_column(String(200))
    case_type: Mapped[str] = mapped_column(String(80))
    court: Mapped[str] = mapped_column(String(120))
    claimant: Mapped[str] = mapped_column(String(120))
    respondent: Mapped[str] = mapped_column(String(120))
    status: Mapped[str] = mapped_column(String(20), default="draft")
    fee_amount: Mapped[str] = mapped_column(String(20), default="")
    fee_receipt: Mapped[str] = mapped_column(String(40), default="")
    judge_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    courtroom: Mapped[str] = mapped_column(String(60), default="")
    case_code: Mapped[Optional[str]] = mapped_column(String(12), unique=True, nullable=True)
    response_due: Mapped[Optional[date]] = mapped_column(Date, nullable=True)
    defects: Mapped[str] = mapped_column(Text, default="")
    registry_note: Mapped[str] = mapped_column(String(255), default="")
    filed_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class CaseCounsel(Base):
    __tablename__ = "case_counsel"
    __table_args__ = (UniqueConstraint("case_id", "user_id", name="uq_case_user"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    side: Mapped[str] = mapped_column(String(12))
    position: Mapped[int] = mapped_column(Integer, default=0)


class Document(Base):
    __tablename__ = "documents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(255))
    doc_type: Mapped[str] = mapped_column(String(40))
    side: Mapped[str] = mapped_column(String(12))
    filed: Mapped[bool] = mapped_column(Boolean, default=False)
    uploaded_by: Mapped[int] = mapped_column(Integer)
    pages: Mapped[int] = mapped_column(Integer, default=1)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Order(Base):
    __tablename__ = "orders"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id", ondelete="CASCADE"), index=True)
    judge_id: Mapped[int] = mapped_column(Integer)
    kind: Mapped[str] = mapped_column(String(30))
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class PrivateNote(Base):
    __tablename__ = "private_notes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    text: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)