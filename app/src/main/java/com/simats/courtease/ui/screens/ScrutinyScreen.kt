package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseStatusPill
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = CE.Muted, fontSize = 15.sp, modifier = Modifier.weight(0.38f))
        Text(value.ifBlank { "-" }, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun ScrutinyScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    var returning by remember { mutableStateOf(false) }

    CourtScaffold(onBack = onBack) {
        if (c == null) {
            EmptyCard("Case not found", "This filing no longer exists.")
            return@CourtScaffold
        }
        val checks = Store.scrutiny(c)
        val allPass = checks.all { it.second }
        val docs = Store.state.docs.filter { it.caseId == c.id && it.side == Side.CLAIMANT }
        val lawyer = Store.user(c.claimantTeam.firstOrNull() ?: 0)

        ScreenHeader("SCRUTINY", c.label, c.title)

        CourtCard {
            Row(Modifier.fillMaxWidth()) {
                Text("Status", color = CE.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                CaseStatusPill(c.status)
            }
            Spacer(Modifier.height(6.dp))
            Fact("Type", c.caseType.substringAfter("· "))
            Fact("Court", c.court)
            Fact("Claimant", c.claimant)
            Fact("Respondent", c.respondent)
            Fact("Filed by", lawyer?.let { "${it.name} (${it.registryId})" }.orEmpty())
            Fact("Filed on", if (c.filedAt > 0) Dates.stamp(c.filedAt) else "")
            Fact("Court fee", if (c.feeAmount.isBlank()) "" else "₹${c.feeAmount} · ${c.feeReceipt}")
        }

        SectionTitle("Documents")
        if (docs.isEmpty()) {
            EmptyCard("No documents", "The lawyer has not uploaded anything.")
        } else {
            CourtCard {
                docs.forEach { d ->
                    Text(d.name, color = CE.Navy, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    BodyText("${d.type} · ${d.pages} pages")
                }
            }
        }

        SectionTitle("Checklist")
        CourtCard {
            checks.forEach { (label, ok) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        if (ok) "✓" else "✗",
                        color = if (ok) CE.Teal else CE.Red,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(label, color = CE.Navy, fontSize = 16.sp)
                }
            }
        }

        when (c.status) {
            CaseStatus.FILED -> {
                if (allPass) {
                    PrimaryButton("Register case", {
                        val result = Store.registerCase(c.id, Session.userId)
                        if (result == null) {
                            Toast.makeText(
                                context, "Registered as ${Store.caseById(c.id)?.caseNo}", Toast.LENGTH_LONG
                            ).show()
                            onBack()
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    })
                } else {
                    WarnBanner("Checks failed", "Return the case so the lawyer can correct it.")
                }
                OutlineButton("Return to lawyer", { returning = true }, Modifier.fillMaxWidth(), color = CE.Red)
            }
            CaseStatus.RETURNED -> {
                SectionTitle("Returned for correction")
                CourtCard {
                    c.defects.forEach { Text("• $it", color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                }
                BodyText("Waiting for the lawyer to correct and file again.")
            }
            else -> BodyText("This case has already passed scrutiny.")
        }
    }

    if (returning && c != null) {
        var note by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val failed = Store.scrutiny(c).filter { !it.second }
        AlertDialog(
            onDismissRequest = { returning = false },
            title = { Text("Return ${c.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (failed.isNotEmpty()) {
                        BodyText("Failed checks are added automatically:")
                        failed.forEach { Text("• ${it.first}", color = CE.Red, fontSize = 14.sp) }
                    }
                    CourtField("Note to the lawyer", note, { note = it; error = null })
                    error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = Store.returnCase(c.id, Session.userId, note)
                    if (result == null) {
                        returning = false
                        Toast.makeText(context, "Case returned to the lawyer", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        error = result
                    }
                }) { Text("Return", color = CE.Red) }
            },
            dismissButton = { TextButton(onClick = { returning = false }) { Text("Cancel") } }
        )
    }
}