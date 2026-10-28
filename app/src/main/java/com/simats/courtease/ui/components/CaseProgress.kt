package com.simats.courtease.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.ui.theme.CE

val CASE_STEPS = listOf("Filing", "Registry", "Notice", "Pleadings", "Decision")

fun caseStep(s: CaseStatus): String = when (s) {
    CaseStatus.DRAFT, CaseStatus.FILED, CaseStatus.RETURNED -> "Filing"
    CaseStatus.REGISTERED, CaseStatus.ALLOCATED -> "Registry"
    CaseStatus.NOTICE -> "Notice"
    CaseStatus.PLEADINGS -> "Pleadings"
    else -> "Decision"
}

fun caseNote(s: CaseStatus): Pair<String, String> = when (s) {
    CaseStatus.DRAFT -> "Draft" to "Add documents, then file to the court"
    CaseStatus.FILED -> "With the registry" to "Waiting for scrutiny"
    CaseStatus.RETURNED -> "Returned for correction" to "Fix the defects and file again"
    CaseStatus.REGISTERED -> "Registered" to "Waiting for allocation to a judge"
    CaseStatus.ALLOCATED -> "Allocated to a judge" to "Waiting for the notice to be issued"
    CaseStatus.NOTICE -> "Notice issued" to "Waiting for the respondent's advocate to appear"
    CaseStatus.PLEADINGS -> "Pleadings" to "Both sides file statements and evidence"
    CaseStatus.REVIEW -> "Under the judge's review" to "The judge is reviewing the record"
    CaseStatus.JUDGMENT -> "Judgment" to "A judgment has been issued"
    CaseStatus.CLOSED -> "Closed" to "The record is read-only"
}

@Composable
fun CaseProgressCard(status: CaseStatus) {
    CourtCard {
        StageProgress(caseStep(status), CASE_STEPS)
        Spacer(Modifier.height(14.dp))
        val (headline, next) = caseNote(status)
        Text(headline, color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        BodyText(next, Modifier.padding(top = 4.dp))
    }
}