import os
import secrets
from datetime import datetime
from typing import List

from fastapi import Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, field_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from database import get_db
from models import (
    CaseCounsel, CaseInvite, Document, TeamMessage, TeamNote, TeamTask, User,
)

UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
MAX_BYTES = 15 * 1024 * 1024
DATE_FORMAT = "%d %b %Y"


# ---------- request and response shapes ----------

class InviteIn(BaseModel):
    email: str

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        v = v.strip().lower()
        if "@" not in v or len(v) < 5:
            raise ValueError("Enter the advocate's email")
        return v


class RespondIn(BaseModel):
    accept: bool


class TextIn(BaseModel):
    text: str

    @field_validator("text")
    @classmethod
    def _text(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Write something first")
        return v[:2000]


class TaskIn(BaseModel):
    assignee_id: int
    title: str
    due: str

    @field_validator("title")
    @classmethod
    def _title(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 2:
            raise ValueError("Enter the task")
        return v[:200]

    @field_validator("due")
    @classmethod
    def _due(cls, v: str) -> str:
        try:
            datetime.strptime(v.strip(), DATE_FORMAT)
        except ValueError:
            raise ValueError("Pick a due date")
        return v.strip()


class InviteOut(BaseModel):
    id: int
    case_id: int
    side: str
    email: str
    invited_by: int
    status: str


class TextOut(BaseModel):
    id: int
    case_id: int
    side: str
    user_id: int
    text: str
    created_at: int


class TaskOut(BaseModel):
    id: int
    case_id: int
    side: str
    creator_id: int
    assignee_id: int
    title: str
    due: str
    done: bool


class TeamSyncOut(BaseModel):
    invites: List[InviteOut]
    messages: List[TextOut]
    notes: List[TextOut]
    tasks: List[TaskOut]


# ---------- file helpers ----------

def sniff(data: bytes):
    if data.startswith(b"%PDF"):
        return "application/pdf", ".pdf"
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg", ".jpg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png", ".png"
    return None, None


def save_upload(upload: UploadFile):
    data = upload.file.read(MAX_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="The file is empty")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=400, detail="The file is larger than 15 MB")
    mime, ext = sniff(data)
    if mime is None:
        raise HTTPException(status_code=400, detail="Only PDF, JPG or PNG files are allowed")
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    stored = secrets.token_hex(16) + ext
    with open(os.path.join(UPLOAD_DIR, stored), "wb") as f:
        f.write(data)
    name = os.path.basename(upload.filename or "") or ("document" + ext)
    return stored, len(data), mime, name[:255]


def install_b3(app, m):
    """Adds the file and team routes. 'm' is main.py, which owns the shared helpers."""

    # ---------------- documents with real files ----------------

    @app.post("/cases/{cid}/upload", response_model=m.DocOut, status_code=201)
    def upload_doc(
            cid: int,
            doc_type: str = Form(...),
            pages: int = Form(1),
            file: UploadFile = File(...),
            user: User = Depends(m.lawyer_user),
            db: Session = Depends(get_db),
    ):
        c = m.get_case(db, cid)
        side = m.side_of(db, c.id, user.id)
        if side is None:
            raise HTTPException(status_code=404, detail="Case not found")
        if c.status == "closed":
            raise HTTPException(status_code=400, detail="This case is closed")
        if doc_type not in m.DOC_TYPES:
            raise HTTPException(status_code=400, detail="Choose a document type")
        if doc_type == "Plaint" and side != "claimant":
            raise HTTPException(status_code=400, detail="Only the claimant files a plaint")
        if doc_type == "Written statement" and side != "respondent":
            raise HTTPException(status_code=400, detail="Only the respondent files a written statement")
        stored, size, mime, name = save_upload(file)
        d = Document(
            case_id=c.id, name=name, doc_type=doc_type, side=side, filed=False,
            uploaded_by=user.id, pages=max(pages, 1), stored_name=stored, size=size, mime=mime,
        )
        db.add(d)
        m.audit(db, user.id, "Uploaded document", f"{m.label(c)} · {doc_type} · {name}")
        db.commit()
        db.refresh(d)
        return m.doc_out(d)

    @app.post("/cases/join-file", response_model=m.CaseOut)
    def join_with_file(
            code: str = Form(...),
            pages: int = Form(1),
            file: UploadFile = File(...),
            user: User = Depends(m.lawyer_user),
            db: Session = Depends(get_db),
    ):
        c = m.by_code(db, code)
        if c is None:
            raise HTTPException(status_code=404, detail="No case found for this code")
        teams = m.teams_of(db, c.id)
        if user.id in teams["claimant"]:
            raise HTTPException(status_code=400, detail="You act for the claimant in this case")
        if user.id in teams["respondent"]:
            raise HTTPException(status_code=400, detail="You already appear in this case")
        if teams["respondent"]:
            raise HTTPException(
                status_code=400,
                detail="A respondent's advocate has already appeared. Ask the lead advocate to invite you.",
            )
        if c.status != "notice":
            raise HTTPException(status_code=400, detail="This case is not open for appearance")
        stored, size, mime, name = save_upload(file)
        db.add(CaseCounsel(case_id=c.id, user_id=user.id, side="respondent", position=0))
        db.add(Document(
            case_id=c.id, name=name, doc_type="Vakalatnama", side="respondent", filed=True,
            uploaded_by=user.id, pages=max(pages, 1), stored_name=stored, size=size, mime=mime,
        ))
        c.status = "pleadings"
        m.tell(
            db, teams["claimant"][:1] + ([c.judge_id] if c.judge_id else []) + m.registry_ids(db),
            "Respondent appeared", f"{user.full_name} appears for the respondent in {m.label(c)}", c.id,
                )
        m.audit(db, user.id, "Appeared for respondent", f"{m.label(c)} · code {c.case_code}")
        db.commit()
        db.refresh(c)
        return m.one_out(db, c, user)

    @app.get("/docs/{did}/content")
    def document_content(
            did: int, user: User = Depends(m.approved_user), db: Session = Depends(get_db)
    ):
        d = db.get(Document, did)
        if d is None:
            raise HTTPException(status_code=404, detail="Document not found")
        c = m.get_case(db, d.case_id)
        allowed = False
        if user.role == "lawyer":
            side = m.side_of(db, c.id, user.id)
            allowed = side is not None and (d.filed or d.side == side)
        elif user.role == "judge":
            allowed = c.judge_id == user.id and d.filed and c.status in m.JUDGE_SEES
        if user.role == "registry":
            raise HTTPException(status_code=403, detail="The registry can't open document contents")
        if not allowed:
            raise HTTPException(status_code=404, detail="Document not found")
        path = os.path.join(UPLOAD_DIR, d.stored_name) if d.stored_name else ""
        if not d.stored_name or not os.path.isfile(path):
            raise HTTPException(status_code=404, detail="No file is stored for this document")
        m.audit(db, user.id, "Opened document", f"{m.label(c)} · {d.name}")
        db.commit()
        return FileResponse(
            path, media_type=d.mime or "application/octet-stream",
            filename=d.name, content_disposition_type="inline",
        )

    # ---------------- team workspace ----------------

    def my_side(db: Session, cid: int, user: User):
        c = m.get_case(db, cid)
        side = m.side_of(db, c.id, user.id)
        if side is None:
            raise HTTPException(status_code=404, detail="Case not found")
        return c, side

    def text_out(row) -> TextOut:
        return TextOut(
            id=row.id, case_id=row.case_id, side=row.side, user_id=row.user_id,
            text=row.text, created_at=m.millis(row.created_at),
        )

    def task_out(t: TeamTask) -> TaskOut:
        return TaskOut(
            id=t.id, case_id=t.case_id, side=t.side, creator_id=t.creator_id,
            assignee_id=t.assignee_id, title=t.title, due=m.fmt_date(t.due), done=t.done,
        )

    def invite_out(i: CaseInvite) -> InviteOut:
        return InviteOut(
            id=i.id, case_id=i.case_id, side=i.side, email=i.email,
            invited_by=i.invited_by, status=i.status,
        )

    @app.get("/sync/team", response_model=TeamSyncOut)
    def sync_team(user: User = Depends(m.approved_user), db: Session = Depends(get_db)):
        empty = TeamSyncOut(invites=[], messages=[], notes=[], tasks=[])
        if user.role != "lawyer":
            return empty
        mine = {r.case_id: r.side for r in db.scalars(select(CaseCounsel).where(CaseCounsel.user_id == user.id))}
        ids = list(mine.keys())

        invites = {i.id: i for i in db.scalars(select(CaseInvite).where(CaseInvite.email == user.email.lower()))}
        messages, notes, tasks = [], [], []
        if ids:
            for i in db.scalars(select(CaseInvite).where(CaseInvite.case_id.in_(ids))):
                if i.side == mine[i.case_id]:
                    invites[i.id] = i
            for row in db.scalars(select(TeamMessage).where(TeamMessage.case_id.in_(ids)).order_by(TeamMessage.id)):
                if row.side == mine[row.case_id]:
                    messages.append(text_out(row))
            for row in db.scalars(select(TeamNote).where(TeamNote.case_id.in_(ids)).order_by(TeamNote.id)):
                if row.side == mine[row.case_id]:
                    notes.append(text_out(row))
            for t in db.scalars(select(TeamTask).where(TeamTask.case_id.in_(ids)).order_by(TeamTask.id)):
                if t.side == mine[t.case_id]:
                    tasks.append(task_out(t))
        return TeamSyncOut(
            invites=[invite_out(i) for i in invites.values()],
            messages=messages, notes=notes, tasks=tasks,
        )

    @app.post("/cases/{cid}/invites", response_model=InviteOut, status_code=201)
    def invite(cid: int, body: InviteIn, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        c, side = my_side(db, cid, user)
        if m.teams_of(db, c.id)[side][:1] != [user.id]:
            raise HTTPException(status_code=403, detail="Only the lead advocate can invite")
        target = db.scalar(select(User).where(User.email == body.email))
        if target is None or target.role != "lawyer" or target.status != "approved":
            raise HTTPException(status_code=400, detail="No approved advocate has this email")
        if m.side_of(db, c.id, target.id) is not None:
            raise HTTPException(status_code=400, detail="Already on this case")
        pending = db.scalar(select(CaseInvite).where(
            CaseInvite.case_id == c.id, CaseInvite.email == body.email, CaseInvite.status == "pending"
        ))
        if pending is not None:
            raise HTTPException(status_code=400, detail="An invitation is already pending")
        inv = CaseInvite(case_id=c.id, side=side, email=body.email, invited_by=user.id)
        db.add(inv)
        m.tell(db, [target.id], "Case invitation", f"{user.full_name} invited you to {c.title}", c.id)
        m.audit(db, user.id, "Invited advocate", f"{m.label(c)} · {body.email}")
        db.commit()
        db.refresh(inv)
        return invite_out(inv)

    @app.post("/invites/{iid}/respond", response_model=InviteOut)
    def respond(iid: int, body: RespondIn, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        inv = db.get(CaseInvite, iid)
        if inv is None or inv.email != user.email.lower() or inv.status != "pending":
            raise HTTPException(status_code=404, detail="This invitation is not available")
        c = m.get_case(db, inv.case_id)
        if body.accept:
            if m.side_of(db, c.id, user.id) is not None:
                raise HTTPException(status_code=400, detail="You are already on this case")
            position = len(m.teams_of(db, c.id)[inv.side])
            db.add(CaseCounsel(case_id=c.id, user_id=user.id, side=inv.side, position=position))
        inv.status = "accepted" if body.accept else "declined"
        m.tell(
            db, [inv.invited_by],
            "Invitation accepted" if body.accept else "Invitation declined",
            f"{user.full_name} · {m.label(c)}", c.id,
        )
        m.audit(db, user.id, "Joined team" if body.accept else "Declined invitation", m.label(c))
        db.commit()
        db.refresh(inv)
        return invite_out(inv)

    @app.post("/cases/{cid}/messages", response_model=TextOut, status_code=201)
    def send_message(cid: int, body: TextIn, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        c, side = my_side(db, cid, user)
        row = TeamMessage(case_id=c.id, side=side, user_id=user.id, text=body.text)
        db.add(row)
        db.commit()
        db.refresh(row)
        return text_out(row)

    @app.post("/cases/{cid}/notes", response_model=TextOut, status_code=201)
    def add_team_note(cid: int, body: TextIn, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        c, side = my_side(db, cid, user)
        row = TeamNote(case_id=c.id, side=side, user_id=user.id, text=body.text)
        db.add(row)
        db.commit()
        db.refresh(row)
        return text_out(row)

    @app.post("/cases/{cid}/tasks", response_model=TaskOut, status_code=201)
    def add_task(cid: int, body: TaskIn, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        c, side = my_side(db, cid, user)
        if body.assignee_id not in m.teams_of(db, c.id)[side]:
            raise HTTPException(status_code=400, detail="Choose someone from your team")
        t = TeamTask(
            case_id=c.id, side=side, creator_id=user.id, assignee_id=body.assignee_id,
            title=body.title, due=datetime.strptime(body.due, DATE_FORMAT).date(),
        )
        db.add(t)
        if body.assignee_id != user.id:
            m.tell(db, [body.assignee_id], "Task assigned", f"{body.title} · due {body.due}", c.id)
        db.commit()
        db.refresh(t)
        return task_out(t)

    @app.post("/tasks/{tid}/toggle", response_model=TaskOut)
    def toggle_task(tid: int, user: User = Depends(m.lawyer_user), db: Session = Depends(get_db)):
        t = db.get(TeamTask, tid)
        if t is None or m.side_of(db, t.case_id, user.id) != t.side:
            raise HTTPException(status_code=404, detail="Task not found")
        t.done = not t.done
        db.commit()
        db.refresh(t)
        return task_out(t)