package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AnalysisRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.IssueDto
import com.simats.courtease.data.api.IssuesDto
import com.simats.courtease.data.api.PassageDto
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
private fun StatusPill(status: String) {
    when (status) {
        "disputed" -> Pill("Appears disputed", CE.Chip, CE.Red)
        "both" -> Pill("Both mention")
        "claimant_only" -> Pill("Plaint only", CE.Chip, CE.Teal)
        "respondent_only" -> Pill("Written statement only", CE.Chip, CE.Teal)
        else -> Pill("Other documents", CE.Chip, CE.Muted)
    }
}

@Composable
fun IssuesSection(caseId: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<IssuesDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewing by remember { mutableStateOf<PassageDto?>(null) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = AnalysisRepo.issues(caseId)) {
                is ApiResult.Ok -> {
                    data = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(caseId) { load() }

    InfoBanner("Issues are found by keyword rules, not by a lawyer or a judge. A word can appear without the sides really disputing the point. Always open the page and check.")
    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
    OutlineButton(if (loading) "Refreshing..." else "Refresh issues", { load() }, Modifier.fillMaxWidth())

    val d = data
    if (d == null) {
        if (error == null) EmptyCard("Loading", "Looking for issues.")
        return
    }

    if (d.unread.isNotEmpty()) {
        WarnBanner(
            "${d.unread.size} document(s) not read yet",
            d.unread.joinToString("\n") { "${it.name}: ${it.reason}" }
        )
        BodyText("Open the Documents tab and tap Read new documents, then refresh.")
    }
    if (!d.havePlaint || !d.haveWrittenStatement) {
        BodyText(
            "Status needs both pleadings. " +
                    (if (!d.havePlaint) "No plaint has been read. " else "") +
                    (if (!d.haveWrittenStatement) "No written statement has been read." else "")
        )
    }

    SectionTitle("${d.issues.size} possible issue(s) from ${d.docsUsed} document(s)")
    if (d.issues.isEmpty()) {
        EmptyCard("No issues found", "Read the documents first, or none of the keywords appear in them.")
    }

    d.issues.forEach { issue -> IssueCard(issue, issue.key in open, {
        open = if (issue.key in open) open - issue.key else open + issue.key
    }) { viewing = it } }

    viewing?.let { p -> PageViewDialog(p) { viewing = null } }
}

@Composable
private fun IssueCard(issue: IssueDto, expanded: Boolean, onToggle: () -> Unit, onView: (PassageDto) -> Unit) {
    CourtCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(issue.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        StatusPill(issue.status)
        BodyText(issue.plain, Modifier.padding(top = 6.dp))
        Text(issue.statusText, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        BodyText("${issue.found} passage(s) found", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        OutlineButton(if (expanded) "Hide passages" else "Show passages", onToggle, Modifier.fillMaxWidth())

        if (expanded) {
            issue.passages.forEach { p ->
                Spacer(Modifier.height(12.dp))
                Text(
                    p.role + if (p.filed) "" else " (draft)", color = CE.Teal,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Text(p.sentence, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                if (p.figures.isNotEmpty()) {
                    Text(
                        "Figures: ${p.figures.joinToString(", ")}", color = CE.Teal,
                        fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (p.denial && p.role == "Written statement") {
                    Text(
                        "Contains a denial or a different account", color = CE.Red,
                        fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                BodyText("${p.docName} · page ${p.page}", Modifier.padding(top = 2.dp))
                OutlineButton("View page", { onView(p) }, Modifier.fillMaxWidth())
            }
            if (issue.found > issue.passages.size) {
                BodyText("Showing ${issue.passages.size} of ${issue.found}.", Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
fun PageViewDialog(p: PassageDto, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Loading...") }

    LaunchedEffect(p.docId, p.page) {
        when (val r = AnalysisRepo.pages(p.docId)) {
            is ApiResult.Ok -> {
                val page = r.data.pages.firstOrNull { it.page == p.page }
                if (page == null) {
                    note = "This page's text is not available."
                } else {
                    text = page.text
                    note = ""
                }
            }
            is ApiResult.Err -> note = r.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${p.docName} · page ${p.page}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Found in this sentence", color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(p.sentence, color = CE.Navy, fontSize = 15.sp)
                Text("Full page text", color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (note.isNotEmpty()) BodyText(note) else Text(text, color = CE.Navy, fontSize = 14.sp)
                BodyText("Extracted text can contain mistakes. Check the original document.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}