import re
from typing import Literal

from pydantic import BaseModel, EmailStr, field_validator


def _check_password(v: str) -> str:
    if len(v) < 8 or not re.search(r"[A-Za-z]", v) or not re.search(r"\d", v):
        raise ValueError("Password needs 8+ characters with letters and a number")
    return v


def _check_phone(v: str) -> str:
    v = re.sub(r"[\s-]", "", v)
    if not re.fullmatch(r"\+?\d{10,15}", v):
        raise ValueError("Enter a valid phone number")
    return v


class SignupIn(BaseModel):
    full_name: str
    email: EmailStr
    phone: str
    role: Literal["judge", "lawyer"]
    reg_no: str
    council: str
    password: str

    @field_validator("full_name")
    @classmethod
    def _name(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 2:
            raise ValueError("Enter your full name")
        return v

    @field_validator("phone")
    @classmethod
    def _phone(cls, v: str) -> str:
        return _check_phone(v)

    @field_validator("reg_no")
    @classmethod
    def _reg(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 4:
            raise ValueError("Enter your bar number or employee ID")
        return v

    @field_validator("council")
    @classmethod
    def _council(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("Enter your bar council or court of posting")
        return v

    @field_validator("password")
    @classmethod
    def _password(cls, v: str) -> str:
        return _check_password(v)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    id: int
    full_name: str
    email: str
    phone: str
    role: str
    status: str
    reg_no: str
    council: str
    registry_id: str
    court: str
    courtroom: str
    reject_reason: str
    created_at: int


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class ResetPasswordIn(BaseModel):
    email: EmailStr
    phone: str
    new_password: str

    @field_validator("phone")
    @classmethod
    def _phone(cls, v: str) -> str:
        return _check_phone(v)

    @field_validator("new_password")
    @classmethod
    def _password(cls, v: str) -> str:
        return _check_password(v)


class ProfileIn(BaseModel):
    full_name: str
    phone: str

    @field_validator("full_name")
    @classmethod
    def _name(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 2:
            raise ValueError("Enter your full name")
        return v

    @field_validator("phone")
    @classmethod
    def _phone(cls, v: str) -> str:
        return _check_phone(v)


class PasswordChangeIn(BaseModel):
    current_password: str
    new_password: str

    @field_validator("new_password")
    @classmethod
    def _password(cls, v: str) -> str:
        return _check_password(v)


class ApproveIn(BaseModel):
    court: str = ""
    courtroom: str = ""


class ReasonIn(BaseModel):
    reason: str

    @field_validator("reason")
    @classmethod
    def _reason(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("Enter a reason")
        return v[:255]


class MessageOut(BaseModel):
    message: str


class NotifOut(BaseModel):
    id: int
    title: str
    body: str
    case_id: int
    created_at: int
    is_read: bool


class AuditOut(BaseModel):
    id: int
    user_name: str
    action: str
    detail: str
    created_at: int