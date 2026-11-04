package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.Store
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.ScreenHeader

@Composable
fun AnalysisScreen(caseId: Int, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val caseLabel: String = Store.caseById(caseId)?.label ?: "Case"
    val subtitle = when (tab) {
        0 -> "Step 1: read the documents"
        1 -> "Step 2: dated events"
        else -> "Step 3: issues and evidence"
    }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("ANALYSIS", caseLabel, subtitle)
        InfoBanner("Automated assistance. Everything runs on the CourtEase server, and no text is sent to another company. Check every item against the source page.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Documents", tab == 0) { tab = 0 }
            ChoiceChip("Timeline", tab == 1) { tab = 1 }
            ChoiceChip("Issues", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> ReadingSection(caseId)
            1 -> TimelineSection(caseId)
            else -> IssuesSection(caseId)
        }
    }
}