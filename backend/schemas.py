import re
from typing import List, Literal

from pydantic import BaseModel, EmailStr, ValidationInfo, field_validator


def _check_password(v: str) -> str:
    if len(v) < 8 or not re.search(r"[A-Za-z]", v) or not re.search(r"\d", v):
        raise ValueError("Password needs 8+ characters with letters and a number")
    return v


def _check_phone(v: str) -> str:
    v = re.sub(r"[\s-]", "", v)
    if not re.fullmatch(r"\+?\d{10,15}", v):
        raise ValueError("Enter a valid phone number")
    return v


# ---------- accounts ----------

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


# ---------- cases ----------

class CaseIn(BaseModel):
    title: str
    case_type: str
    court: str
    claimant: str
    respondent: str
    fee_amount: str = ""
    fee_receipt: str = ""

    @field_validator("title", "case_type", "court", "claimant", "respondent")
    @classmethod
    def _required(cls, v: str, info: ValidationInfo) -> str:
        v = v.strip()
        if not v:
            raise ValueError(f"{info.field_name.replace('_', ' ').title()} is required")
        return v[:200]

    @field_validator("fee_amount", "fee_receipt")
    @classmethod
    def _fee(cls, v: str) -> str:
        return v.strip()[:40]


class CaseOut(BaseModel):
    id: int
    case_no: str
    title: str
    case_type: str
    court: str
    claimant: str
    respondent: str
    status: str
    claimant_team: List[int]
    respondent_team: List[int]
    fee_amount: str
    fee_receipt: str
    judge_id: int
    courtroom: str
    case_code: str
    response_due: str
    defects: List[str]
    registry_note: str
    filed_at: int


class PreviewOut(BaseModel):
    id: int
    case_no: str
    title: str
    case_type: str
    court: str
    claimant: str
    respondent: str
    response_due: str


class DocIn(BaseModel):
    name: str
    doc_type: str
    pages: int = 1

    @field_validator("name")
    @classmethod
    def _name(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Choose a file")
        return v[:255]


class DocOut(BaseModel):
    id: int
    case_id: int
    name: str
    doc_type: str
    side: str
    filed: bool
    uploaded_by: int
    pages: int
    created_at: int


class JoinIn(BaseModel):
    code: str
    file_name: str
    pages: int = 1


class ReturnIn(BaseModel):
    note: str = ""


class AllocateIn(BaseModel):
    judge_id: int
    reason: str = ""


class OrderIn(BaseModel):
    kind: Literal["Direction", "Interim order", "Judgment"]
    title: str
    body: str

    @field_validator("title")
    @classmethod
    def _title(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("Give the order a title")
        return v[:200]

    @field_validator("body")
    @classmethod
    def _body(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 10:
            raise ValueError("Write the order text")
        return v


class NoteIn(BaseModel):
    text: str

    @field_validator("text")
    @classmethod
    def _text(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Write the note first")
        return v


class OrderOut(BaseModel):
    id: int
    case_id: int
    judge_id: int
    kind: str
    title: str
    body: str
    created_at: int


class NoteOut(BaseModel):
    id: int
    case_id: int
    user_id: int
    text: str
    created_at: int


class SyncOut(BaseModel):
    cases: List[CaseOut]
    docs: List[DocOut]
    orders: List[OrderOut]
    notes: List[NoteOut]