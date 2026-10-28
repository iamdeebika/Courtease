package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.RegistryCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.theme.CE

@Composable
fun AllocationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cases = Store.state.cases
    val waiting = cases.filter { it.status == CaseStatus.REGISTERED }
    val recent = cases.filter { it.judgeId != 0 }.sortedByDescending { it.id }.take(5)
    val judges = Store.approvedJudges()
    var selected by remember { mutableStateOf<CaseFile?>(null) }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Allocation", "${waiting.size} awaiting a judge")

        SectionTitle("Judge load")
        if (judges.isEmpty()) EmptyCard("No judges", "Approve a judge account first.")
        judges.forEach { j ->
            CourtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(j.name, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Pill("${Store.openCasesOf(j.id)} open")
                }
                BodyText("${j.court} · ${j.courtroom}", Modifier.padding(top = 4.dp))
            }
        }

        SectionTitle("Awaiting allocation")
        if (waiting.isEmpty()) EmptyCard("All clear", "Registered cases appear here.")
        waiting.forEach { c ->
            RegistryCaseCard(c, detail = "Tap to allocate", onClick = { selected = c })
        }

        SectionTitle("Recently allocated")
        if (recent.isEmpty()) EmptyCard("None yet", "Allocated cases appear here.")
        recent.forEach { c ->
            val judge = Store.user(c.judgeId)
            RegistryCaseCard(c, detail = "Before ${judge?.name.orEmpty()} · ${c.courtroom}")
        }
    }

    selected?.let { c ->
        AllocateDialog(
            c = c,
            onResult = { message ->
                selected = null
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun AllocateDialog(c: CaseFile, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val judges = Store.approvedJudges()
    val suggestion = Store.suggestJudge(c.court)
    val options = judges.associate {
        "${it.name} · ${it.courtroom} · ${Store.openCasesOf(it.id)} open" to it.id
    }
    var chosen by remember {
        mutableStateOf(
            suggestion?.let { s -> options.entries.firstOrNull { it.value == s.id }?.key }
                ?: options.keys.firstOrNull().orEmpty()
        )
    }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allocate ${c.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BodyText(c.title)
                BodyText("Filing court: ${c.court}")
                if (suggestion != null) {
                    BodyText("Suggested: ${suggestion.name} (fewest open cases in this court)")
                } else {
                    BodyText("No judge is posted to this court. Choose any approved judge.")
                }
                if (options.isEmpty()) {
                    Text("No approved judge is available.", color = CE.Red, fontSize = 14.sp)
                } else {
                    DropdownField("Judge", chosen, options.keys.toList(), { chosen = it; error = null })
                    CourtField("Reason (needed if you change the suggestion)", reason, { reason = it; error = null })
                }
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            if (options.isNotEmpty()) {
                TextButton(onClick = {
                    val judgeId = options[chosen] ?: 0
                    val result = Store.allocate(c.id, Session.userId, judgeId, reason)
                    if (result == null) {
                        val j = Store.user(judgeId)
                        onResult("Allocated to ${j?.name}, ${j?.courtroom}")
                    } else {
                        error = result
                    }
                }) { Text("Allocate", color = CE.Teal) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}