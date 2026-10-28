package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.rememberDocPicker
import com.simats.courtease.ui.theme.CE

private val CLAIMANT_TYPES = listOf("Plaint", "Vakalatnama", "Evidence", "Annexure", "Other")
private val RESPONDENT_TYPES = listOf("Written statement", "Evidence", "Annexure", "Other")

@Composable
fun DocumentsScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    val side = c?.let { CaseOps.sideOf(it, me) }
    var type by remember { mutableStateOf("") }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val pick = rememberDocPicker { name, pages ->
        if (c != null) {
            val chosen = type.ifBlank { if (side == Side.RESPONDENT) RESPONDENT_TYPES.first() else CLAIMANT_TYPES.first() }
            toast(CaseOps.addDoc(c.id, me, name, chosen, pages) ?: "Added as a draft")
        }
    }

    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
        val types = if (side == Side.RESPONDENT) RESPONDENT_TYPES else CLAIMANT_TYPES
        val lead = CaseOps.isLead(c, me)
        val docs = CaseOps.visibleDocs(c, me)

        ScreenHeader("DOCUMENTS", c.label, "Drafts are private to your side. Filed documents are seen by the judge and the other side.")

        if (c.status != CaseStatus.CLOSED) {
            CourtCard {
                Text("Add a document", color = CE.Navy, fontSize = 19.sp)
                Spacer(Modifier.height(12.dp))
                DropdownField("Type", type.ifBlank { types.first() }, types, { type = it })
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Choose PDF or image", { pick() })
            }
        }

        SectionTitle("${docs.size} document(s)")
        if (docs.isEmpty()) EmptyCard("Nothing yet", "Upload your first document above.")
        docs.forEach { d ->
            val mine = d.side == side
            CourtCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.name, color = CE.Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    if (d.filed) Pill("Filed") else Pill("Draft", CE.Chip, CE.Muted)
                }
                BodyText(
                    "${d.type} · ${d.pages} page(s) · ${Dates.stamp(d.at)}",
                    Modifier.padding(top = 4.dp)
                )
                if (!mine) {
                    Text(
                        "Filed by the other side", color = CE.Teal, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (mine && !d.filed) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlineButton("Remove", {
                            toast(CaseOps.removeDoc(d.id, me) ?: "Removed")
                        }, Modifier.weight(1f), color = CE.Red)
                        if (lead) {
                            PrimaryButton("File to court", {
                                toast(CaseOps.fileDoc(d.id, me) ?: "Filed to the court")
                            }, Modifier.weight(1f))
                        }
                    }
                    if (!lead) BodyText("The lead advocate files documents to the court.", Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}