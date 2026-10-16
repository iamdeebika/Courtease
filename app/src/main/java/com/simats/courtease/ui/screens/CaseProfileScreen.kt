package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.CaseRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseProfileScreen(caseNo: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val c = CaseRepository.get(caseNo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Case Profile") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (c == null) {
            Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Case not found", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(c.caseNo, style = MaterialTheme.typography.headlineSmall)
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text("Court: ${c.court}")
                Text("Type: ${c.caseType}")
                Text("Next hearing: ${c.nextHearing}")
                Text("${c.pages} pages digitised, ${c.documents} documents")
                Spacer(Modifier.height(16.dp))
                Text("Case Workspace", style = MaterialTheme.typography.titleMedium)
                listOf("Document Analysis", "Issue Identification", "Precedent Retrieval").forEach {
                    OutlinedButton(
                        onClick = { /* coming in later sprints */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(it)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        CaseRepository.delete(c.caseNo)
                        onDeleted()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Case")
                }
            }
        }
    }
}
