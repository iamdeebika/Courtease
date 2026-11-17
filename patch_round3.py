import os
import re
import sys

root = os.path.dirname(os.path.abspath(__file__))
comp = os.path.join(root, "app", "src", "main", "java", "com", "simats", "courtease", "ui", "components")

STAGE_NEW = """@Composable
private fun StageCell(index: Int, count: Int, current: Int, label: String, modifier: Modifier) {
    val done = index < current
    val active = index == current
    val dotColor by animateColorAsState(
        targetValue = if (done || active) CE.Teal else CE.Track, animationSpec = tween(500), label = "dot"
    )
    val leftColor by animateColorAsState(
        targetValue = if (index <= current) CE.Teal else CE.Track, animationSpec = tween(500), label = "left"
    )
    val rightColor by animateColorAsState(
        targetValue = if (index < current) CE.Teal else CE.Track, animationSpec = tween(500), label = "right"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(3.dp)
                    .background(if (index == 0) Color.Transparent else leftColor)
            )
            Box(
                Modifier
                    .size(if (active) 28.dp else 22.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else dotColor)
                    .border(if (active) 3.dp else 0.dp, if (active) CE.Teal else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                } else if (active) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(CE.Teal))
                }
            }
            Box(
                Modifier.weight(1f).height(3.dp)
                    .background(if (index == count - 1) Color.Transparent else rightColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
            color = if (active) CE.Teal else CE.Muted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun StageProgress(stage: String, stages: List<String>) {
    val current = stages.indexOf(stage).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { i, s -> StageCell(i, stages.size, current, s, Modifier.weight(1f)) }
    }
}

"""

EMPTY_NEW = """@Composable
fun EmptyCard(title: String, text: String) {
    // Screens that say "Loading" get shimmering placeholder bars instead of plain text.
    if (title.equals("Loading", ignoreCase = true)) {
        LoadingCard()
        return
    }
    CourtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(CE.Mint),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Info, null, tint = CE.Teal) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                BodyText(text, Modifier.padding(top = 2.dp))
            }
        }
    }
}
"""

DROPDOWN_NEW = """@Composable
fun DropdownField(
    label: String, value: String, options: List<String>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        PickerBox(value.ifBlank { "Select" }, Icons.Default.KeyboardArrowDown) { open = true }
    }
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


def read(name):
    with open(os.path.join(comp, name), encoding="utf-8") as f:
        return f.read().replace("\r\n", "\n")


def swap(text, pattern, new, label):
    hits = re.findall(pattern, text, flags=re.S)
    if len(hits) != 1:
        sys.exit("Could not find exactly one place to change for %s (found %d). Nothing was changed." % (label, len(hits)))
    return re.sub(pattern, lambda m: new, text, count=1, flags=re.S)


def add_import(text, anchor, new, label):
    if re.search("^" + re.escape(new) + "$", text, flags=re.M):
        return text
    m = re.search("^" + re.escape(anchor) + "$", text, flags=re.M)
    if not m:
        sys.exit("Could not find the import line '%s' in %s. Nothing was changed." % (anchor, label))
    return text[:m.end()] + "\n" + new + text[m.end():]


common = read("Common.kt")
forms = read("CommonForms.kt")
changed = []

if "private fun StageCell" not in common:
    common = swap(common, r"@Composable\nprivate fun StageBar\(.*?(?=@Composable\nprivate fun LoadingCard\(\))", STAGE_NEW, "the stage bar")
    common = swap(common, r"@Composable\nfun EmptyCard\(title: String, text: String\) \{.*?\n\}\n", EMPTY_NEW, "the empty card")
    common = add_import(common, "import androidx.compose.material.icons.filled.Info", "import androidx.compose.material.icons.filled.Check", "Common.kt")
    changed.append("Common.kt")

if "UiSheet(title = label" not in forms:
    forms = swap(forms, r"@Composable\nfun DropdownField\(.*?\n\}\n", DROPDOWN_NEW, "the drop-down field")
    forms = swap(forms, r"@Composable\nfun SearchField\(.*?\n\}\n", SEARCH_NEW, "the search field")
    forms = add_import(forms, "import androidx.compose.material.icons.filled.DateRange", "import androidx.compose.material.icons.filled.Check", "CommonForms.kt")
    forms = add_import(forms, "import androidx.compose.material.icons.filled.DateRange", "import androidx.compose.material.icons.filled.Close", "CommonForms.kt")
    forms = add_import(forms, "import androidx.compose.material3.Icon", "import androidx.compose.material3.IconButton", "CommonForms.kt")
    changed.append("CommonForms.kt")

for name, text in (("Common.kt", common), ("CommonForms.kt", forms)):
    if name in changed:
        with open(os.path.join(comp, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)

print("Changed:", ", ".join(changed) if changed else "nothing (already updated)")
