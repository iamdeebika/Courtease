import json
import os
import threading
import time
from collections import defaultdict, deque

LOGIN_WINDOW = 600
LOGIN_MAX_PER_EMAIL = 8
LOGIN_MAX_PER_IP = 25
SIGNUP_WINDOW = 3600
SIGNUP_MAX_PER_IP = 10
MAX_TRACKED = 5000

HIDE_DOCS = os.getenv("APP_ENV", "").strip().lower() == "production"
DOC_PATHS = ("/docs", "/docs/oauth2-redirect", "/redoc", "/openapi.json")

_lock = threading.Lock()
_fail_email = defaultdict(deque)
_fail_ip = defaultdict(deque)
_signups = defaultdict(deque)


def _prune(q, window, now):
    while q and now - q[0] > window:
        q.popleft()


def _wait(q, window, now, maximum):
    """Seconds until the count drops below the maximum, or 0 when it already is."""
    if len(q) < maximum:
        return 0
    oldest_that_matters = q[len(q) - maximum]
    return max(int(window - (now - oldest_that_matters)) + 1, 1)


def _trim(table):
    if len(table) > MAX_TRACKED:
        table.clear()


def login_wait(ip, email):
    now = time.time()
    with _lock:
        a = _fail_ip[ip]
        _prune(a, LOGIN_WINDOW, now)
        waits = [_wait(a, LOGIN_WINDOW, now, LOGIN_MAX_PER_IP)]
        if email:
            b = _fail_email[email]
            _prune(b, LOGIN_WINDOW, now)
            waits.append(_wait(b, LOGIN_WINDOW, now, LOGIN_MAX_PER_EMAIL))
        return max(waits)


def login_failed(ip, email):
    now = time.time()
    with _lock:
        _trim(_fail_ip)
        _trim(_fail_email)
        _fail_ip[ip].append(now)
        if email:
            _fail_email[email].append(now)


def login_ok(email):
    with _lock:
        _fail_email.pop(email, None)


def signup_wait(ip):
    now = time.time()
    with _lock:
        _trim(_signups)
        q = _signups[ip]
        _prune(q, SIGNUP_WINDOW, now)
        wait = _wait(q, SIGNUP_WINDOW, now, SIGNUP_MAX_PER_IP)
        if wait == 0:
            q.append(now)
        return wait


def minutes_text(seconds):
    minutes = max((seconds + 59) // 60, 1)
    return "about 1 minute" if minutes == 1 else f"about {minutes} minutes"


async def send_json(send, status, body, extra=None):
    data = json.dumps(body).encode("utf-8")
    headers = [
        (b"content-type", b"application/json"),
        (b"content-length", str(len(data)).encode()),
        (b"cache-control", b"no-store"),
        (b"x-content-type-options", b"nosniff"),
    ]
    for key, value in (extra or {}).items():
        headers.append((key.lower().encode(), str(value).encode()))
    await send({"type": "http.response.start", "status": status, "headers": headers})
    await send({"type": "http.response.body", "body": data})


class Guard:
    """Plain ASGI middleware: login and signup limits, safer headers, optional hidden API docs."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        path = scope.get("path", "")
        method = scope.get("method", "")
        client = scope.get("client")
        ip = client[0] if client else "unknown"
        is_doc = path in DOC_PATHS

        if HIDE_DOCS and is_doc:
            await send_json(send, 404, {"detail": "Not found"})
            return

        async def secure_send(message):
            if message["type"] == "http.response.start":
                headers = list(message.get("headers", []))
                present = {name.lower() for name, _value in headers}
                if b"x-content-type-options" not in present:
                    headers.append((b"x-content-type-options", b"nosniff"))
                if b"referrer-policy" not in present:
                    headers.append((b"referrer-policy", b"no-referrer"))
                if b"cache-control" not in present and not is_doc:
                    headers.append((b"cache-control", b"no-store"))
                message = dict(message)
                message["headers"] = headers
            await send(message)

        if method == "POST" and path == "/auth/signup":
            wait = signup_wait(ip)
            if wait:
                await send_json(
                    send, 429,
                    {"detail": f"Too many signup requests from this device. Try again in {minutes_text(wait)}."},
                    {"Retry-After": wait},
                )
                return

        if method == "POST" and path == "/auth/login":
            body = b""
            while True:
                message = await receive()
                if message["type"] == "http.disconnect":
                    return
                body += message.get("body", b"")
                if not message.get("more_body", False):
                    break

            email = ""
            try:
                parsed = json.loads(body.decode("utf-8"))
                if isinstance(parsed, dict):
                    email = str(parsed.get("email", "")).strip().lower()[:255]
            except Exception:
                email = ""

            wait = login_wait(ip, email)
            if wait:
                print(f"Login locked for {email or ip} for {wait} s")
                await send_json(
                    send, 429,
                    {"detail": f"Too many wrong attempts. Try again in {minutes_text(wait)}."},
                    {"Retry-After": wait},
                )
                return

            sent = False

            async def replay():
                nonlocal sent
                if not sent:
                    sent = True
                    return {"type": "http.request", "body": body, "more_body": False}
                return await receive()

            seen = {"status": 0}

            async def watch(message):
                if message["type"] == "http.response.start":
                    seen["status"] = message["status"]
                await secure_send(message)

            await self.app(scope, replay, watch)
            if seen["status"] == 401:
                login_failed(ip, email)
            elif seen["status"] == 200 and email:
                login_ok(email)
            return

        await self.app(scope, receive, secure_send)


def install_guard(app, m):
    app.add_middleware(Guard)
    print("Guard installed. API docs hidden:", HIDE_DOCS)