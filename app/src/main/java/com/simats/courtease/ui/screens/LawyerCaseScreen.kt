package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.simats.courtease.ui.components.AuthGold
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseProgressCard
import com.simats.courtease.ui.components.CaseStatusPill
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.ToolRow
import com.simats.courtease.ui.theme.CE

@Composable
fun LawyerCaseScreen(
    caseId: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDocs: () -> Unit,
    onTeam: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    val side = c?.let { CaseOps.sideOf(it, me) }
    var confirmFile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            EmptyCard("Case not found", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
        val lead = CaseOps.isLead(c, me)
        val judge = if (c.judgeId != 0) Store.user(c.judgeId) else null
        val docs = CaseOps.visibleDocs(c, me)
        val members = CaseOps.teamOf(c, side).size
        val orders = Store.state.orders.filter { it.caseId == c.id }.sortedByDescending { it.at }

        ScreenHeader("CASE", c.label)

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CE.Navy).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.label, color = Color.White, fontSize = 28.sp, modifier = Modifier.weight(1f))
                CaseStatusPill(c.status)
            }
            Text(c.title, color = Color.White, fontSize = 20.sp)
            Text("${c.caseType.substringAfter("· ")} | ${c.court}", color = Color.White, fontSize = 16.sp)
            Text("${c.claimant}  vs  ${c.respondent}", color = Color(0xFFB4C4D2), fontSize = 16.sp)
            Text(
                if (side == Side.CLAIMANT) "You appear for the claimant" else "You appear for the respondent",
                color = AuthGold, fontSize = 15.sp
            )
            if (judge != null) Text("Before ${judge.name} · ${c.courtroom}", color = Color(0xFFB4C4D2), fontSize = 16.sp)
            if (c.caseCode.isNotBlank() && side == Side.CLAIMANT) {
                Text("Notice code ${c.caseCode} · response due ${c.responseDue}", color = Color(0xFFB4C4D2), fontSize = 15.sp)
            }
        }

        CaseProgressCard(c.status)

        if (c.status == CaseStatus.RETURNED) {
            SectionTitle("Returned for correction")
            CourtCard(color = CE.Amber, borderColor = CE.Amber) {
                c.defects.forEach {
                    Text("• $it", color = CE.AmberText, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        if (CaseOps.editable(c)) {
            if (lead) {
                PrimaryButton("File to court", { confirmFile = true })
                OutlineButton("Edit details", onEdit, Modifier.fillMaxWidth(), Icons.Default.Edit)
                if (c.status == CaseStatus.DRAFT) {
                    OutlineButton("Delete draft", { confirmDelete = true }, Modifier.fillMaxWidth(), Icons.Default.Delete, CE.Red)
                }
            } else {
                BodyText("Only the lead advocate can edit or file this case.")
            }
        }

        SectionTitle("Workspace")
        ToolRow(Icons.AutoMirrored.Filled.List, "Documents", "${docs.size} on file or in draft", onDocs)
        ToolRow(Icons.Default.Person, "Team workspace", "$members advocate(s) · chat, notes, tasks", onTeam)

        SectionTitle("Orders")
        if (orders.isEmpty()) {
            EmptyCard("No orders yet", "Orders issued by the judge appear here.")
        } else {
            orders.forEach { o ->
                CourtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Pill(o.kind)
                    }
                    BodyText(o.body, Modifier.padding(top = 4.dp))
                    BodyText(Dates.stamp(o.at), Modifier.padding(top = 2.dp))
                }
            }
        }
    }

    if (confirmFile && c != null) {
        AlertDialog(
            onDismissRequest = { confirmFile = false },
            title = { Text("File to court?") },
            text = { Text("The registry will check the filing. You can't edit it while it is with the registry.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFile = false
                    val r = CaseOps.fileCase(c.id, me)
                    Toast.makeText(context, r ?: "Filed. The registry will check it.", Toast.LENGTH_LONG).show()
                }) { Text("File", color = CE.Teal) }
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
                    val r = CaseOps.deleteDraft(c.id, me)
                    if (r == null) onDeleted() else Toast.makeText(context, r, Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = CE.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}