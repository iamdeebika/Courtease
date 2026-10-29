package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.data.Session
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.JudgeCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField

@Composable
fun JudgeCasesScreen(onBack: () -> Unit, onOpenCase: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Open") }

    val all = JudgeOps.casesOf(Session.userId)
    val shown = all
        .filter {
            when (filter) {
                "Open" -> it.status != CaseStatus.CLOSED && it.status != CaseStatus.JUDGMENT
                "Decided" -> it.status == CaseStatus.JUDGMENT || it.status == CaseStatus.CLOSED
                else -> true
            }
        }
        .filter {
            query.isBlank() || it.title.contains(query, true) || it.label.contains(query, true) ||
                    it.claimant.contains(query, true) || it.respondent.contains(query, true)
        }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("CAUSE LIST", "Cases before the court", "${all.size} allocated to you")
        SearchField(query, { query = it }, "Search by title, number or party")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Open", "Decided", "All").forEach { f -> ChoiceChip(f, filter == f) { filter = f } }
        }
        if (shown.isEmpty()) EmptyCard("No cases", "Nothing matches this filter.")
        shown.forEach { c -> JudgeCaseCard(c) { onOpenCase(c.id) } }
    }
}