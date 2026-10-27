import os
import smtplib
from email.message import EmailMessage


def send_email(to: str, subject: str, body: str) -> None:
    host = os.getenv("SMTP_HOST", "")
    if not host:
        # No mail server set up: print to this window so you can test.
        print(f"\n[DEV EMAIL] To: {to}\nSubject: {subject}\n{body}\n")
        return

    msg = EmailMessage()
    msg["From"] = os.getenv("SMTP_FROM") or os.getenv("SMTP_USER", "")
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    try:
        with smtplib.SMTP(host, int(os.getenv("SMTP_PORT", "587")), timeout=15) as server:
            server.starttls()
            server.login(os.getenv("SMTP_USER", ""), os.getenv("SMTP_PASSWORD", ""))
            server.send_message(msg)
    except Exception as exc:
        print("Email failed:", exc)