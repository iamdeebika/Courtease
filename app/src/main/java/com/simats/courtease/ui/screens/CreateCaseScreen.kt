package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.model.CivilCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCaseScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    var caseNo by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var court by remember { mutableStateOf("") }
    var caseType by remember { mutableStateOf("") }
    var nextHearing by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Case") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = caseNo, onValueChange = { caseNo = it },
                label = { Text("Case number (e.g. CE-2026-0170)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title (e.g. Ravi vs Kumar)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = court, onValueChange = { court = it },
                label = { Text("Court") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = caseType, onValueChange = { caseType = it },
                label = { Text("Case type (e.g. Contract Dispute)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nextHearing, onValueChange = { nextHearing = it },
                label = { Text("Next hearing (e.g. 24 Oct 2026)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            error?.let { Text(it) }

            Button(
                onClick = {
                    when {
                        caseNo.isBlank() || title.isBlank() || court.isBlank() ->
                            error = "Case number, title and court are required."
                        CaseRepository.exists(caseNo.trim()) ->
                            error = "A case with this number already exists."
                        else -> {
                            CaseRepository.add(
                                CivilCase(
                                    caseNo = caseNo.trim(),
                                    title = title.trim(),
                                    court = court.trim(),
                                    caseType = caseType.trim().ifBlank { "Civil" },
                                    nextHearing = nextHearing.trim().ifBlank { "Not set" },
                                    pages = 0,
                                    documents = 0
                                )
                            )
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save case") }
        }
    }
}
