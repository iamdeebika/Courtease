import os
import sys

root = os.path.dirname(os.path.abspath(__file__))
path = os.path.join(root, "app", "src", "main", "java", "com", "simats", "courtease", "ui", "screens", "DocumentsScreen.kt")
if not os.path.exists(path):
    sys.exit("DocumentsScreen.kt was not found. Nothing was changed.")
with open(path, encoding="utf-8") as f:
    text = f.read().replace("\r\n", "\n")

if "var waited" in text:
    print("Already patched.")
    sys.exit(0)

old = """    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
"""
new = """    // A case that was just created may not have reached this phone yet, so ask the server again before giving up.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(caseId, c == null) {
        if (c == null) {
            AccountRepo.syncAll()
            delay(2000)
            AccountRepo.syncAll()
            delay(2000)
            waited = true
        }
    }

    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            if (waited) {
                EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            } else {
                EmptyCard("Loading", "Fetching your case.")
            }
            return@CourtScaffold
        }
"""
if text.count(old) != 1:
    sys.exit("Could not find the place to change. Nothing was changed.")
text = text.replace(old, new)

anchor = "import androidx.compose.runtime.Composable"
if anchor not in text:
    sys.exit("Could not find the import line. Nothing was changed.")
for imp in ("import androidx.compose.runtime.LaunchedEffect", "import com.simats.courtease.data.AccountRepo", "import kotlinx.coroutines.delay"):
    if imp not in text:
        text = text.replace(anchor, anchor + "\n" + imp, 1)

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("Patched DocumentsScreen.kt")
