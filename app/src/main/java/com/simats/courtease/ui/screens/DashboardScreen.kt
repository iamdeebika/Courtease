package com.simats.courtease.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.model.CivilCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    role: String,
    onOpenCase: (String) -> Unit,
    onCreateCase: () -> Unit,
    onLogout: () -> Unit,
) {
    val cases by CaseRepository.cases.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${role.lowercase().replaceFirstChar { it.uppercase() }} Dashboard")
                },
                actions = { TextButton(onClick = onLogout) { Text("Logout") } },
            )
        },
        floatingActionButton = {
            if (role.equals("LAWYER", ignoreCase = true)) {
                FloatingActionButton(onClick = onCreateCase) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(cases) { c -> CaseCard(c) { onOpenCase(c.caseNo) } }
        }
    }
}

@Composable
private fun CaseCard(c: CivilCase, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(c.caseNo, style = MaterialTheme.typography.labelLarge)
            Text(c.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("${c.caseType} | ${c.court}", style = MaterialTheme.typography.bodySmall)
            Text("Next hearing: ${c.nextHearing}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
