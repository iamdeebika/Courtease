import os
import sys
import time
from datetime import datetime, timedelta

path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "firebase-key.json")
if not os.path.isfile(path):
    sys.exit("firebase-key.json was not found in the backend folder.")
made = datetime.fromtimestamp(os.path.getmtime(path))
age = (time.time() - os.path.getmtime(path)) / 86400
due = made + timedelta(days=30)
print("The Firebase key file is dated", made.strftime("%d %b %Y"), "(%d days old)." % int(age))
print("Replace it by", due.strftime("%d %b %Y"), "(30 days after it was created).")
if age >= 30:
    print("OVERDUE: replace it now.")
    sys.exit(1)
if age >= 25:
    print("Replace it soon.")
