package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.FileOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.IntegrityBadge
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.UiEmpty
import com.simats.courtease.ui.components.UiSheet
import com.simats.courtease.ui.components.rememberDocPicker
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

private val CLAIMANT_TYPES = listOf("Plaint", "Vakalatnama", "Evidence", "Annexure", "Other")
private val RESPONDENT_TYPES = listOf("Written statement", "Evidence", "Annexure", "Other")

@Composable
private fun FileBadge(ext: String, filed: Boolean) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filed) CE.Teal else CE.Navy),
        contentAlignment = Alignment.Center
    ) { Text(ext, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun DocumentsScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    val side = c?.let { CaseOps.sideOf(it, me) }
    var type by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Triple<Int, String, String>?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val pick = rememberDocPicker { picked ->
        if (c != null && !busy) {
            val chosen = type.ifBlank {
                if (side == Side.RESPONDENT) RESPONDENT_TYPES.first() else CLAIMANT_TYPES.first()
            }
            busy = true
            scope.launch {
                val r = FileOps.uploadDoc(context, c.id, chosen, picked)
                busy = false
                toast(r ?: "Uploaded as a draft")
            }
        }
    }

    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
        val types = if (side == Side.RESPONDENT) RESPONDENT_TYPES else CLAIMANT_TYPES
        val selected = type.ifBlank { types.first() }
        val lead = CaseOps.isLead(c, me)
        val docs = CaseOps.visibleDocs(c, me)
        val filedCount = docs.count { it.filed }

        ScreenHeader(
            "DOCUMENTS", c.label,
            "Drafts are private to your side. Filed documents are seen by the judge and the other side."
        )

        if (c.status != CaseStatus.CLOSED) {
            CourtCard {
                Text("Add a document", color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                BodyText("Choose what it is, then pick the file. It is saved as a private draft.", Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t -> ChoiceChip(t, selected == t) { type = t } }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    if (busy) "Uploading..." else "Choose PDF, JPG or PNG",
                    { if (!busy) pick() },
                    icon = Icons.Default.Add
                )
                BodyText("Up to 15 MB.", Modifier.padding(top = 8.dp))
            }
        }

        SectionTitle("${docs.size} document(s)")
        if (docs.isNotEmpty()) BodyText("$filedCount filed · ${docs.size - filedCount} draft")
        if (docs.isEmpty()) {
            UiEmpty(
                Icons.Default.Add, "No documents yet",
                "Upload your first document above. It stays private until you file it."
            )
        }

        docs.forEach { d ->
            val mine = d.side == side
            val ext = d.name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "DOC" }
            CourtCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FileBadge(ext, d.filed)
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        BodyText("${d.type} · ${d.pages} page(s)", Modifier.padding(top = 2.dp))
                        BodyText(Dates.stamp(d.at))
                    }
                    if (d.filed) Pill("Filed") else Pill("Draft", CE.Chip, CE.Muted)
                }
                IntegrityBadge(d.id, d.filed, d.hasFile)
                if (!mine) {
                    Text(
                        "Filed by the other side", color = CE.Teal, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (d.hasFile) {
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Open", {
                        scope.launch { FileOps.open(context, d)?.let { toast(it) } }
                    }, Modifier.fillMaxWidth())
                } else {
                    BodyText("Sample entry with no file attached", Modifier.padding(top = 6.dp))
                }
                if (mine && !d.filed) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlineButton("Remove", { pending = Triple(d.id, "remove", d.name) }, Modifier.weight(1f), color = CE.Red)
                        if (lead) {
                            PrimaryButton("File to court", { pending = Triple(d.id, "file", d.name) }, Modifier.weight(1f))
                        }
                    }
                    if (!lead) BodyText("The lead advocate files documents to the court.", Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    pending?.let { item ->
        val id = item.first
        val filing = item.second == "file"
        UiSheet(
            title = if (filing) "File this document to court?" else "Remove this draft?",
            onDismiss = { pending = null },
            subtitle = item.third
        ) {
            BodyText(
                if (filing) "Once filed, it can't be removed. The judge and the other side will be able to see it."
                else "The draft will be deleted. This can't be undone."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton("Cancel", { pending = null }, Modifier.weight(1f))
                PrimaryButton(if (filing) "File to court" else "Remove", {
                    pending = null
                    scope.launch {
                        val r = if (filing) CaseRepo.fileDoc(id) else CaseRepo.removeDoc(id)
                        toast(r ?: if (filing) "Filed to the court" else "Removed")
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}
