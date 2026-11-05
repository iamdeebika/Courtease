package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AnalysisRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.SummaryDto
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

private fun kindName(kind: String): String = when (kind) {
    "amount" -> "money amounts"
    "quantity" -> "quantities"
    "date" -> "dates"
    "denied" -> "denials"
    else -> "topics without a supporting document"
}

@Composable
fun SummaryCard(caseId: Int, refreshKey: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<SummaryDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun fetch() {
        when (val r = AnalysisRepo.summary(caseId)) {
            is ApiResult.Ok -> {
                data = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
    }

    LaunchedEffect(caseId, refreshKey) { fetch() }

    val d = data
    if (d == null) {
        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
        if (error == null) BodyText("Loading summary...")
        return
    }

    if (d.stale) {
        WarnBanner("Documents changed since your last review", d.changes.joinToString("\n"))
    }

    CourtCard {
        Text("Summary", color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        BodyText(d.scope, Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))

        val read = buildString {
            append("${d.docsAnalysed} document(s) analysed")
            if (d.ocrPages > 0) append(", ${d.ocrPages} page(s) read by OCR")
        }
        Text(read, color = CE.Navy, fontSize = 15.sp)
        if (d.docsUnread > 0) {
            Text("${d.docsUnread} document(s) not read yet", color = CE.Red, fontSize = 15.sp)
        }
        Text("${d.events} dated event(s)", color = CE.Navy, fontSize = 15.sp)
        Text(
            "${d.issues} possible issue(s), ${d.issuesDisputed} appear disputed",
            color = CE.Navy, fontSize = 15.sp
        )
        Text("${d.flags} point(s) to verify", color = CE.Navy, fontSize = 15.sp)
        if (d.flagsByKind.isNotEmpty()) {
            BodyText(d.flagsByKind.joinToString(", ") { "${it.count} ${kindName(it.kind)}" })
        }
        if (!d.havePlaint || !d.haveWrittenStatement) {
            BodyText(
                "Comparing the two sides needs both pleadings. " +
                        (if (!d.havePlaint) "No plaint read. " else "") +
                        (if (!d.haveWrittenStatement) "No written statement read." else ""),
                Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        BodyText(
            if (d.everReviewed) "Last reviewed ${Dates.stamp(d.lastReviewed)}"
            else "You have not marked this analysis as reviewed yet."
        )
        Spacer(Modifier.height(10.dp))
        val label = if (busy) "Saving..." else "Mark as reviewed"
        val onMark: () -> Unit = {
            if (!busy) {
                busy = true
                scope.launch {
                    val problem = AnalysisRepo.markSeen(caseId)
                    if (problem != null) error = problem else fetch()
                    busy = false
                }
            }
        }
        if (d.stale || !d.everReviewed) PrimaryButton(label, onMark)
        else OutlineButton(label, onMark, Modifier.fillMaxWidth())
        error?.let { Text(it, color = CE.Red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}