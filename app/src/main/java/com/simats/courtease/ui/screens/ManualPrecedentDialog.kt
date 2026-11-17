package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.LawRepo
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.UiCheck
import com.simats.courtease.ui.components.UiField
import com.simats.courtease.ui.components.UiSheet
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay
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
    var titleError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            delay(900)
            onDone()
        }
    }

    UiSheet(
        title = "Add a precedent",
        onDismiss = onDismiss,
        subtitle = "Type the details of a judgment you found. Only you can see it. The app does not check the citation or the link, so check them yourself."
    ) {
        if (saved) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UiCheck()
                Spacer(Modifier.height(14.dp))
                Text("Saved", color = CE.Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            UiField(
                "Case name", title, { title = it; titleError = null; error = null },
                placeholder = "A v. B", error = titleError
            )
            UiField(
                "Citation", citation, { citation = it; error = null },
                placeholder = "e.g. (2017) 10 SCC 1", maxChars = 80
            )
            UiField("Court", court, { court = it; error = null }, placeholder = "e.g. Supreme Court of India")
            UiField("Date of judgment", date, { date = it; error = null }, placeholder = "e.g. 24 Aug 2017")
            UiField(
                "Key point, in your own words", summary, { summary = it; error = null },
                lines = 3, maxChars = 600
            )
            UiField(
                "Link to the judgment", link, { link = it; error = null },
                placeholder = "https://indiankanoon.org/doc/..."
            )
            UiField("Private note", note, { note = it; error = null }, lines = 2, maxChars = 500)
            BodyText("Don't paste the judgment's text. A short key point in your own words is enough.")
            error?.let { Text(it, color = CE.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            PrimaryButton(if (busy) "Saving..." else "Save precedent", {
                if (!busy) {
                    if (title.trim().length < 3) {
                        titleError = "Enter the case name"
                    } else {
                        busy = true
                        scope.launch {
                            val problem = LawRepo.saveManual(caseId, title, citation, court, date, summary, link, note)
                            busy = false
                            if (problem == null) saved = true else error = problem
                        }
                    }
                }
            })
            OutlineButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
