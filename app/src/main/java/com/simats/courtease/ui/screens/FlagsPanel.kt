package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.simats.courtease.data.api.FlagDto
import com.simats.courtease.data.api.FlagsDto
import com.simats.courtease.data.api.PassageDto
import com.simats.courtease.data.api.PassageFlagDto
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

private val KIND_ORDER = listOf("amount", "quantity", "date", "denied", "unsupported")

private fun kindTitle(kind: String): String = when (kind) {
    "amount" -> "Money amounts differ"
    "quantity" -> "Quantities differ"
    "date" -> "Dates differ"
    "denied" -> "Denied in the written statement"
    else -> "No supporting document found"
}

private fun leftLabel(kind: String) = if (kind == "denied" || kind == "unsupported") "Plaint (claimant)" else "Claimant's side"
private fun rightLabel(kind: String) = if (kind == "denied" || kind == "unsupported") "Written statement (respondent)" else "Respondent's side"

private fun asPassage(p: PassageFlagDto) = PassageDto(
    docId = p.docId, docName = p.docName, docType = p.docType, side = p.side, filed = p.filed,
    page = p.page, method = p.method, role = p.role, sentence = p.sentence,
    figures = emptyList(), denial = false
)

@Composable
fun FlagsSection(caseId: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FlagsDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewing by remember { mutableStateOf<PassageDto?>(null) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = AnalysisRepo.flags(caseId)) {
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

    InfoBanner("These are points to verify, not findings. Simple rules compare numbers and dates between the two sides and note denials. The app never says who is right. A person must check each one against the source page.")
    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
    OutlineButton(if (loading) "Refreshing..." else "Refresh", { load() }, Modifier.fillMaxWidth())

    val d = data
    if (d == null) {
        if (error == null) EmptyCard("Loading", "Comparing the documents.")
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
            "Comparing the two sides needs both pleadings. " +
                    (if (!d.havePlaint) "No plaint has been read. " else "") +
                    (if (!d.haveWrittenStatement) "No written statement has been read." else "")
        )
    }
    if (!d.haveEvidence) {
        BodyText("No evidence documents (receipts, challans, notices) have been read, so the list of topics without a supporting document may be long.")
    }

    if (d.flags.isEmpty()) {
        EmptyCard("Nothing to verify", "No differences, denials or unsupported topics were found by the rules. That does not mean the documents agree on everything.")
    }

    KIND_ORDER.forEach { kind ->
        val group = d.flags.filter { it.kind == kind }
        if (group.isNotEmpty()) {
            SectionTitle("${kindTitle(kind)} (${group.size})")
            group.forEach { f ->
                FlagCard(f, f.key in open, {
                    open = if (f.key in open) open - f.key else open + f.key
                }) { viewing = asPassage(it) }
            }
        }
    }

    viewing?.let { p -> PageViewDialog(p) { viewing = null } }
}

@Composable
private fun FlagCard(f: FlagDto, expanded: Boolean, onToggle: () -> Unit, onView: (PassageFlagDto) -> Unit) {
    CourtCard {
        Text(f.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Pill("Statement requires verification", CE.Chip, CE.Red)
        BodyText(f.summary, Modifier.padding(top = 8.dp))

        if (f.claimantValues.isNotEmpty() || f.respondentValues.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(leftLabel(f.kind), color = CE.Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    f.claimantValues.forEach { Text(it, color = CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(rightLabel(f.kind), color = CE.Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    f.respondentValues.forEach { Text(it, color = CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlineButton(if (expanded) "Hide sources" else "Show sources", onToggle, Modifier.fillMaxWidth())

        if (expanded) {
            PassageList(leftLabel(f.kind), f.claimant, onView)
            PassageList(rightLabel(f.kind), f.respondent, onView)
        }
    }
}

@Composable
private fun PassageList(label: String, items: List<PassageFlagDto>, onView: (PassageFlagDto) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(label, color = CE.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    if (items.isEmpty()) {
        BodyText("Nothing found on this side.", Modifier.padding(top = 2.dp))
    }
    items.forEach { p ->
        Text(p.sentence, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        BodyText(
            "${p.docName} · page ${p.page}" + if (p.filed) "" else " · draft",
            Modifier.padding(top = 2.dp)
        )
        OutlineButton("View page", { onView(p) }, Modifier.fillMaxWidth())
    }
}