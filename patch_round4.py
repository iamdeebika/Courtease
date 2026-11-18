import os
import re
import sys

root = os.path.dirname(os.path.abspath(__file__))
ui = os.path.join(root, "app", "src", "main", "java", "com", "simats", "courtease", "ui")
comp = os.path.join(ui, "components")
scr = os.path.join(ui, "screens")

WARN_NEW = """@Composable
fun WarnBanner(title: String, text: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    val base = Modifier
        .cardEntrance()
        .fillMaxWidth()
        .clip(shape)
        .background(CE.Amber)
        .border(1.dp, CE.Gold.copy(alpha = 0.5f), shape)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(m.padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Warning, null, tint = CE.AmberText) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = CE.AmberText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = CE.AmberText, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
"""

SECTION_NEW = """@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(CE.Teal))
        Spacer(Modifier.width(10.dp))
        Text(title, color = CE.Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .background(CE.Mint)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
"""

COURTFIELD_NEW = """@Composable
fun CourtField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    masked: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = CE.Muted) }
        } else null,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}
"""

SEARCH_NEW = """@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = CE.Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = CE.Teal) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Clear", tint = CE.Muted) } }
        } else null,
        shape = RoundedCornerShape(30.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
"""

DROPDOWN_NEW = """@Composable
private fun PickerField(
    label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(icon, null, tint = CE.Teal) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick))
    }
}

@Composable
fun DropdownField(
    label: String, value: String, options: List<String>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, value, Icons.Default.KeyboardArrowDown, { open = true }, modifier)
    if (open) {
        UiSheet(title = label, onDismiss = { open = false }) {
            options.forEach { o ->
                val picked = o == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (picked) CE.Mint else Color.Transparent)
                        .clickable { onSelect(o); open = false }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o, color = if (picked) CE.Teal else CE.Navy, fontSize = 17.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (picked) Icon(Icons.Default.Check, null, tint = CE.Teal)
                }
            }
        }
    }
}
"""

DATE_NEW = """@Composable
fun DateField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PickerField(label, value, Icons.Default.DateRange, onClick, modifier)
}
"""


def read(path):
    if not os.path.exists(path):
        sys.exit("File not found: " + path + ". Nothing was changed.")
    with open(path, encoding="utf-8") as f:
        return f.read().replace("\r\n", "\n").rstrip("\n") + "\n"


def swap(text, pattern, new, label):
    found = re.findall(pattern, text, flags=re.S)
    if len(found) != 1:
        sys.exit("Could not find exactly one place to change for %s (found %d). Nothing was changed." % (label, len(found)))
    return re.sub(pattern, lambda m: new, text, count=1, flags=re.S)


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        sys.exit("Could not find exactly one place to change for %s (found %d). Nothing was changed." % (label, n))
    return text.replace(old, new)


def add_import(text, anchor, new, label):
    if re.search("^" + re.escape(new) + "$", text, flags=re.M):
        return text
    m = re.search("^" + re.escape(anchor) + "$", text, flags=re.M)
    if not m:
        sys.exit("Could not find the import line '%s' in %s. Nothing was changed." % (anchor, label))
    return text[:m.end()] + "\n" + new + text[m.end():]


out = {}
notes = []

# Common.kt: warning banner with an icon, section titles with an accent bar, equal-height stat cards.
common_path = os.path.join(comp, "Common.kt")
common = read(common_path)
if "Icons.Default.Warning" not in common:
    common = swap(common, r"@Composable\nfun WarnBanner\(.*?\n\}\n", WARN_NEW, "the warning banner")
    common = swap(common, r"@Composable\nfun SectionTitle\(.*?\n\}\n", SECTION_NEW, "the section title")
    common = add_import(common, "import androidx.compose.material.icons.filled.Info",
                        "import androidx.compose.material.icons.filled.Warning", "Common.kt")
if "heightIn(min = 112.dp)" not in common:
    common = once(common, "    CourtCard(modifier) {",
                  "    CourtCard(modifier.heightIn(min = 112.dp).fillMaxHeight()) {", "the stat card")
if common != read(common_path):
    out[common_path] = common

# CommonForms.kt: floating-label fields, sheet drop-downs, matching date picker, clearable search.
forms_path = os.path.join(comp, "CommonForms.kt")
forms = read(forms_path)
if "unfocusedLabelColor" not in forms:
    forms = once(forms, "    cursorColor = CE.Teal\n)",
                 "    cursorColor = CE.Teal,\n    focusedLabelColor = CE.Teal,\n    unfocusedLabelColor = CE.Muted\n)",
                 "the field colours")
if "label = { Text(label) }," not in forms.split("fun CourtField(")[1].split("fun SearchField(")[0]:
    forms = swap(forms, r"@Composable\nfun CourtField\(.*?\n\}\n", COURTFIELD_NEW, "the text field")
if "Icons.Default.Close" not in forms:
    forms = swap(forms, r"@Composable\nfun SearchField\(.*?\n\}\n", SEARCH_NEW, "the search field")
if "private fun PickerField(" not in forms:
    forms = swap(forms, r"@Composable\nfun DropdownField\(.*?\n\}\n", DROPDOWN_NEW, "the drop-down field")
    forms = swap(forms, r"@Composable\nfun DateField\(.*?\n\}\n", DATE_NEW, "the date field")
for new in ("import androidx.compose.material.icons.filled.Check", "import androidx.compose.material.icons.filled.Close"):
    forms = add_import(forms, "import androidx.compose.material.icons.filled.DateRange", new, "CommonForms.kt")
forms = add_import(forms, "import androidx.compose.material3.Icon", "import androidx.compose.material3.IconButton", "CommonForms.kt")
if forms != read(forms_path):
    out[forms_path] = forms

# UiDash.kt: equal-height icon stat cards.
dash_path = os.path.join(comp, "UiDash.kt")
dash = read(dash_path)
if "heightIn(min = 96.dp)" not in dash:
    dash = once(dash, "    CourtCard(modifier) {",
                "    CourtCard(modifier.heightIn(min = 96.dp).fillMaxHeight()) {", "the icon stat card")
    out[dash_path] = dash

# The three dashboards: rows of stat cards share one height.
old_row = "Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {"
new_row = "Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {"
for name in ("LawyerDashboardScreen.kt", "RegistryDashboardScreen.kt", "JudgeDashboardScreen.kt"):
    p = os.path.join(scr, name)
    if not os.path.exists(p):
        notes.append(name + ": file not found, skipped")
        continue
    t = read(p)
    if "IntrinsicSize.Max" in t:
        continue
    if t.count(old_row) == 0:
        notes.append(name + ": no stat row found, skipped")
        continue
    t = t.replace(old_row, new_row)
    t = add_import(t, "import androidx.compose.foundation.layout.Row", "import androidx.compose.foundation.layout.IntrinsicSize", name)
    t = add_import(t, "import androidx.compose.foundation.layout.Row", "import androidx.compose.foundation.layout.height", name)
    out[p] = t

for path, text in out.items():
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)

print("Changed:", ", ".join(os.path.basename(p) for p in out) if out else "nothing (already updated)")
for n in notes:
    print("Note:", n)

