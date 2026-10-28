package com.simats.courtease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.theme.CE

@Composable
fun LawyerCaseCard(c: CaseFile, me: Int, onClick: () -> Unit) {
    val side = CaseOps.sideOf(c, me)
    val judge = if (c.judgeId != 0) Store.user(c.judgeId) else null
    val detail = when {
        c.status == CaseStatus.RETURNED -> "${c.defects.size} defect(s) to correct"
        c.status == CaseStatus.NOTICE -> "Notice issued · response due ${c.responseDue}"
        judge != null -> "Before ${judge.name} · ${c.courtroom}"
        else -> null
    }
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                c.label, color = CE.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (side != null) Pill(if (side == Side.CLAIMANT) "Claimant" else "Respondent", CE.Chip, CE.Navy)
            CaseStatusPill(c.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(c.title, color = CE.Navy, fontSize = 20.sp)
        BodyText("${c.caseType.substringAfter("· ")} · ${c.court}", Modifier.padding(top = 4.dp))
        if (detail != null) {
            Text(
                detail, color = if (c.status == CaseStatus.RETURNED) CE.Red else CE.Teal,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}