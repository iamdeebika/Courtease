# Decides which alerts also go out as push notifications. The first matching rule wins.
RULES = [
    (True, ("reminder", "overdue", "due ")),
    (True, ("respondent", "appeared")),
    (False, ("chat", "message", "task", "note", "joined", "welcome", "saved", "analysis")),
    (True, ("returned", "correction", "registered", "allocated", "notice", "order", "direction",
            "judgment", "judgement", "approved", "rejected", "suspended", "reinstated", "closed",
            "stepped aside", "invit", "filed")),
]


def is_important(title, body):
    text = ((title or "") + " " + (body or "")).lower()
    for important, words in RULES:
        if any(w in text for w in words):
            return important
    return False
