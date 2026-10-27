import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, field_validator


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

    @field_validator("password")
    @classmethod
    def _password(cls, v: str) -> str:
        return _check_password(v)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    full_name: str
    email: str
    phone: str
    role: str


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


class MessageOut(BaseModel):
    message: str