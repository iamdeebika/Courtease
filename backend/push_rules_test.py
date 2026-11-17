import sys

from push_rules import is_important

cases = [
    ("Case returned", "Your case was returned for correction", True),
    ("Case registered", "Your case was registered as OS 7/2026", True),
    ("Direction issued", "A direction was issued", True),
    ("Judgment issued", "The judge issued a judgment", True),
    ("Notice issued", "The notice was issued", True),
    ("Respondent appeared", "The respondent's advocate appeared", True),
    ("Case allocated", "Your case was allocated to a judge", True),
    ("Reminder", "Response due tomorrow", True),
    ("Overdue", "A response is overdue", True),
    ("Account approved", "Your account was approved", True),
    ("Invitation", "You were invited to the team", True),
    ("Case closed", "The case was closed", True),
    ("New message", "Vikram sent a message", False),
    ("Task assigned", "A task was assigned to you", False),
    ("Note added", "A private note was added", False),
    ("Member joined", "Someone joined the team", False),
    ("Welcome", "Welcome to CourtEase", False),
    ("Something else", "Nothing special here", False),
]

bad = 0
for title, body, expected in cases:
    ok = is_important(title, body) == expected
    bad += 0 if ok else 1
    print(("PASS  " if ok else "FAIL  ") + title + "  ->  " + ("push" if expected else "in-app only"))
print()
print("PASS %d   FAIL %d" % (len(cases) - bad, bad))
sys.exit(1 if bad else 0)
