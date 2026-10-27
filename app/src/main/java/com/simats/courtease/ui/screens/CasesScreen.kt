package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.data.Session
import com.simats.courtease.model.STAGES
import com.simats.courtease.ui.components.CaseCard
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.theme.CE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CasesScreen(onBack: () -> Unit, onOpenCase: (String) -> Unit, onCreate: () -> Unit) {
    val cases by CaseRepository.cases.collectAsState()
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("All") }

    val filtered = cases.filter {
        (stage == "All" || it.stage == stage) &&
                (query.isBlank() || it.title.contains(query, true) || it.caseNo.contains(query, true) ||
                        it.claimant.contains(query, true) || it.respondent.contains(query, true))
    }

    CourtScaffold(
        onBack = onBack,
        fab = {
            ExtendedFloatingActionButton(
                onClick = onCreate, containerColor = CE.Teal, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New case") }
            )
        },
        bottomSpace = 96.dp
    ) {
        ScreenHeader("ALL CASES", "Civil cases", "${Session.role.designation} · ${cases.size} in total")
        SearchField(query, { query = it }, "Search by title, number or party")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + STAGES).forEach { s -> ChoiceChip(s, stage == s) { stage = s } }
        }
        if (filtered.isEmpty()) EmptyCard("No cases found", "Change the search or stage filter.")
        else filtered.forEach { c -> CaseCard(c) { onOpenCase(c.caseNo) } }
    }
}