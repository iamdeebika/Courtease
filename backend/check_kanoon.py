import httpx

URL = "https://api.kanoon.dev/v1/search"
try:
    r = httpx.get(URL, params={"q": "bail", "limit": 2}, timeout=15,
                  headers={"User-Agent": "CourtEase-college-project", "Accept": "application/json"})
    print("HTTP status:", r.status_code)
    print("Retry-After:", r.headers.get("Retry-After"))
    print("Body (first 600 characters):")
    print(r.text[:600])
except Exception as exc:
    print("Could not reach kanoon.dev from this PC.")
    print("Reason:", type(exc).__name__, exc)