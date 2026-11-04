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
import com.simats.courtease.data.api.TimelineDto
import com.simats.courtease.data.api.TimelineEventDto
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun niceDate(iso: String): String = try {
    val p = iso.split("-")
    "${p[2].toInt()} ${MONTH_NAMES[p[1].toInt() - 1]} ${p[0]}"
} catch (e: Exception) {
    iso
}

@Composable
fun TimelineSection(caseId: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<TimelineDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var viewing by remember { mutableStateOf<TimelineEventDto?>(null) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = AnalysisRepo.timeline(caseId)) {
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

    InfoBanner("Dates are found automatically in the text of the documents. Tap View page to check each one against the source. A date written in an unusual way can be missed.")
    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
    OutlineButton(if (loading) "Refreshing..." else "Refresh timeline", { load() }, Modifier.fillMaxWidth())

    val d = data
    if (d == null) {
        if (error == null) EmptyCard("Loading", "Building the timeline.")
    } else {
        if (d.unread.isNotEmpty()) {
            WarnBanner(
                "${d.unread.size} document(s) not read yet",
                d.unread.joinToString("\n") { "${it.name}: ${it.reason}" }
            )
            BodyText("Open the Documents tab and tap Read new documents, then refresh.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Claimant", "Respondent").forEach { f ->
                ChoiceChip(f, filter == f) { filter = f }
            }
        }

        val events = d.events.filter {
            filter == "All" || it.side.equals(filter, ignoreCase = true)
        }
        SectionTitle("${events.size} dated event(s) from ${d.docsUsed} document(s)")

        if (events.isEmpty()) {
            EmptyCard("No dates found", "Read the documents first, or the text may contain no dates.")
        }

        events.forEach { e ->
            CourtCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        niceDate(e.dateIso), color = CE.Navy, fontSize = 19.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    Pill(e.label)
                }
                Text(e.sentence, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                if (e.amounts.isNotEmpty()) {
                    Text(
                        "Amounts mentioned: ${e.amounts.joinToString(", ")}", color = CE.Teal,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                BodyText(
                    "${e.docName} · page ${e.page} · ${e.side.replaceFirstChar { it.uppercase() }} · " +
                            (if (e.filed) "Filed" else "Draft") + " · " +
                            (if (e.method == "ocr") "read by OCR" else "typed text"),
                    Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton("View page", { viewing = e }, Modifier.fillMaxWidth())
            }
        }
    }

    viewing?.let { e -> SourcePageDialog(e) { viewing = null } }
}

@Composable
private fun SourcePageDialog(e: TimelineEventDto, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Loading...") }

    LaunchedEffect(e.docId, e.page) {
        when (val r = AnalysisRepo.pages(e.docId)) {
            is ApiResult.Ok -> {
                val page = r.data.pages.firstOrNull { it.page == e.page }
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
        title = { Text("${e.docName} · page ${e.page}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Found in this sentence", color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(e.sentence, color = CE.Navy, fontSize = 15.sp)
                Text("Full page text", color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (note.isNotEmpty()) BodyText(note) else Text(text, color = CE.Navy, fontSize = 14.sp)
                BodyText("Extracted text can contain mistakes. Check the original document.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}