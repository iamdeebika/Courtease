package com.simats.courtease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.data.Session
import com.simats.courtease.model.DemoData
import com.simats.courtease.model.STAGES
import com.simats.courtease.model.stageNote
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StageProgress
import com.simats.courtease.ui.components.ToolRow
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.rememberCase
import com.simats.courtease.ui.theme.CE

@Composable
fun CaseWorkspaceScreen(
    caseNo: String,
    onBack: () -> Unit,
    onDocs: () -> Unit,
    onAnalysis: () -> Unit,
    onIssues: () -> Unit,
    onPrecedent: () -> Unit,
    onHearing: () -> Unit,
    onVerify: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val c = rememberCase(caseNo)
    var confirm by remember { mutableStateOf(false) }

    CourtScaffold(onBack = onBack) {
        if (c == null) {
            EmptyCard("Case not found", "This case may have been deleted.")
            return@CourtScaffold
        }
        ScreenHeader("04 / ${c.caseNo}", "Case workspace", "One case. Every source in context.")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CE.Navy).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(c.caseNo, color = Color.White, fontSize = 30.sp, modifier = Modifier.weight(1f))
                Pill(c.stage)
            }
            Text(c.title, color = Color.White, fontSize = 20.sp)
            Text("${c.caseType.substringAfter("· ")} | ${c.court}", color = Color.White, fontSize = 17.sp)
            Text("Role: ${Session.role.designation}", color = Color(0xFFB4C4D2), fontSize = 16.sp)
            Text("Next Hearing: ${c.nextHearing}", color = Color.White, fontSize = 18.sp)
            Text("${c.pages} pages digitised — ${c.documents} documents", color = Color(0xFFB4C4D2), fontSize = 16.sp)
            Text("Access: Role-Based | Audit Log ON", color = Color(0xFFB4C4D2), fontSize = 14.sp)
        }

        SectionTitle("Case progress")
        CourtCard {
            StageProgress(c.stage, STAGES)
            Spacer(Modifier.height(14.dp))
            val (headline, next) = stageNote(c.stage)
            Text(headline, color = CE.Navy, fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            BodyText(next, Modifier.padding(top = 4.dp))
        }

        SectionTitle("Case tools")
        ToolRow(Icons.AutoMirrored.Filled.List, "Document Analysis", "${c.documents} documents · Search & OCR", onDocs)
        ToolRow(Icons.Default.DateRange, "Timeline", "Chronological events · 92% generated", onAnalysis)
        ToolRow(Icons.Default.Info, "Issues & Evidence", "${DemoData.issues.size} disputed issues · 84% mapped", onIssues)
        ToolRow(Icons.Default.Search, "Precedent Retrieval", "Applicable law and related judgments", onPrecedent)
        ToolRow(Icons.Default.Create, "Hearing Preparation", "Summary, issues and law in one view", onHearing)
        ToolRow(Icons.Default.CheckCircle, "Verification & Report", "${DemoData.statements.size} statements require verification", onVerify)

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Edit", onEdit, Modifier.weight(1f), Icons.Default.Edit)
            OutlineButton("Delete", { confirm = true }, Modifier.weight(1f), Icons.Default.Delete, CE.Red)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this case?") },
            text = { Text("The case record will be removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    CaseRepository.delete(caseNo)
                    onDeleted()
                }) { Text("Delete", color = CE.Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}