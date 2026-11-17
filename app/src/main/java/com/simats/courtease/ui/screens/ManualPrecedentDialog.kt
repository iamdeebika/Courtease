package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.LawRepo
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtArea
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun ManualPrecedentDialog(caseId: Int, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var citation by remember { mutableStateOf("") }
    var court by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a precedent I found") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyText("Type the details from the judgment you found. Only you can see what you save. The app does not check the citation or the link, so check them yourself.")
                CourtField("Case name", title, { title = it; error = null }, placeholder = "A v. B")
                CourtField("Citation", citation, { citation = it; error = null }, placeholder = "e.g. (2017) 10 SCC 1")
                CourtField("Court", court, { court = it; error = null }, placeholder = "e.g. Supreme Court of India")
                CourtField("Date of judgment", date, { date = it; error = null }, placeholder = "e.g. 24 Aug 2017")
                CourtArea("Key point, in your own words", summary, { summary = it; error = null })
                CourtField("Link to the judgment", link, { link = it; error = null }, placeholder = "https://indiankanoon.org/doc/...")
                CourtArea("Private note", note, { note = it; error = null })
                BodyText("Don't paste the judgment's text. A short key point in your own words is enough.")
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (busy) return@TextButton
                busy = true
                scope.launch {
                    val problem = LawRepo.saveManual(caseId, title, citation, court, date, summary, link, note)
                    busy = false
                    if (problem == null) onDone() else error = problem
                }
            }) { Text(if (busy) "Saving..." else "Save", color = CE.Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}