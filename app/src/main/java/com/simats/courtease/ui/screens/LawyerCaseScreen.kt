package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseProgressCard
import com.simats.courtease.ui.components.CaseStatusPill
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.LawLinks
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.ToolRow
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = CE.Muted, fontSize = 15.sp, modifier = Modifier.weight(0.38f))
        Text(value.ifBlank { "-" }, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun LawyerCaseScreen(
    caseId: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDocs: () -> Unit,
    onTeam: () -> Unit,
    onLaw: () -> Unit = {},
    onSavedLaw: () -> Unit = {},
    onAnalysis: () -> Unit = {},
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    var confirmFile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    CourtScaffold(onBack = onBack) {
        val side = c?.let { CaseOps.sideOf(it, me) }
        if (c == null || side == null) {
            EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
        val lead = CaseOps.isLead(c, me)
        val editable = CaseOps.editable(c)
        val claimantLead = Store.user(c.claimantTeam.firstOrNull() ?: 0)
        val respondentLead = Store.user(c.respondentTeam.firstOrNull() ?: 0)
        val judge = Store.user(c.judgeId)
        val docCount = CaseOps.visibleDocs(c, me).size

        ScreenHeader(
            if (side == Side.CLAIMANT) "CLAIMANT'S SIDE" else "RESPONDENT'S SIDE",
            c.label, c.title
        )

        CourtCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Status", color = CE.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                CaseStatusPill(c.status)
            }
            Spacer(Modifier.height(6.dp))
            Line("Type", c.caseType.substringAfter("· "))
            Line("Court", c.court)
            Line("Claimant", c.claimant)
            Line("Respondent", c.respondent)
            Line("Claimant's advocate", claimantLead?.name.orEmpty())
            Line("Respondent's advocate", respondentLead?.name ?: "Not yet appeared")
            if (c.feeAmount.isNotBlank()) Line("Court fee", "₹${c.feeAmount} · ${c.feeReceipt}")
            if (judge != null) Line("Before", "${judge.name}, ${c.courtroom}")
            if (c.responseDue.isNotBlank()) Line("Response due", c.responseDue)
            if (c.filedAt > 0) Line("Filed on", Dates.stamp(c.filedAt))
        }

        CaseProgressCard(c.status)

        if (c.status == CaseStatus.RETURNED) {
            WarnBanner("Returned by the registry", "Correct the points below, then file again.")
            CourtCard {
                c.defects.forEach {
                    Text("• $it", color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        if (side == Side.CLAIMANT && c.caseCode.isNotBlank()) {
            InfoBanner("Case code ${c.caseCode}. Share it with the respondent's advocate so they can appear. Response due ${c.responseDue}.")
        }

        SectionTitle("Case work")
        if (editable && lead) ToolRow(Icons.Default.Create, "Edit details", "Parties, type, court fee", onEdit)
        ToolRow(Icons.Default.Email, "Documents", "$docCount document(s)", onDocs)
        ToolRow(Icons.Default.Person, "Team workspace", "Chat, notes and tasks", onTeam)
        LawLinks(onLaw, onSavedLaw, onAnalysis)

        if (editable && lead) {
            PrimaryButton(
                if (c.status == CaseStatus.RETURNED) "File again to court" else "File to court",
                { confirmFile = true }
            )
        }
        if (c.status == CaseStatus.DRAFT && lead) {
            OutlineButton("Delete draft", { confirmDelete = true }, Modifier.fillMaxWidth(), color = CE.Red)
        }
        if (editable && !lead) {
            BodyText("Only the lead advocate can edit and file this case.")
        }
    }

    if (confirmFile && c != null) {
        AlertDialog(
            onDismissRequest = { confirmFile = false },
            title = { Text("File ${c.label}?") },
            text = { Text("Your documents are filed with the case and the registry checks them. Filed documents can't be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFile = false
                    scope.launch {
                        val r = CaseRepo.fileCase(c.id)
                        Toast.makeText(context, r ?: "Filed. The registry will check it.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("File", color = CE.Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmFile = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this draft?") },
            text = { Text("The draft and its documents are removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val r = CaseRepo.deleteDraft(c.id)
                        if (r == null) onDeleted() else Toast.makeText(context, r, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = CE.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}