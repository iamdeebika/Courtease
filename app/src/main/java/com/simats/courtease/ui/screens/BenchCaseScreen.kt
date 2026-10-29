package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.AuthGold
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseProgressCard
import com.simats.courtease.ui.components.CaseStatusPill
import com.simats.courtease.ui.components.CourtArea
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE

@Composable
fun BenchCaseScreen(caseId: Int, onBack: () -> Unit, onRecused: () -> Unit) {
    val context = LocalContext.current
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    var ordering by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var recusing by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    CourtScaffold(onBack = onBack) {
        if (c == null || c.judgeId != me) {
            EmptyCard("Case not available", "This case is not allocated to you.")
            return@CourtScaffold
        }
        val claimantLead = Store.user(c.claimantTeam.firstOrNull() ?: 0)
        val respondentLead = Store.user(c.respondentTeam.firstOrNull() ?: 0)
        val late = JudgeOps.overdue(c)
        val orders = JudgeOps.ordersOf(c)
        val docs = JudgeOps.filedDocs(c)
        val notes = JudgeOps.notesOf(c, me)

        val canReview = c.status == CaseStatus.PLEADINGS || late
        val canOrder = c.status == CaseStatus.NOTICE || c.status == CaseStatus.PLEADINGS || c.status == CaseStatus.REVIEW
        val canClose = c.status != CaseStatus.CLOSED
        val canRecuse = c.status == CaseStatus.ALLOCATED || c.status == CaseStatus.NOTICE ||
                c.status == CaseStatus.PLEADINGS || c.status == CaseStatus.REVIEW

        ScreenHeader("BENCH VIEW", c.label)

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CE.Navy).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
                CaseStatusPill(c.status)
            }
            Text("${c.caseType.substringAfter("· ")} | ${c.court}", color = Color.White, fontSize = 16.sp)
            Text("${c.courtroom}", color = AuthGold, fontSize = 16.sp)
            Text("${c.claimant}  vs  ${c.respondent}", color = Color.White, fontSize = 18.sp)
            Text(
                "For the claimant: ${claimantLead?.name ?: "-"}",
                color = Color(0xFFB4C4D2), fontSize = 15.sp
            )
            Text(
                "For the respondent: ${respondentLead?.name ?: "not yet appeared"}",
                color = Color(0xFFB4C4D2), fontSize = 15.sp
            )
            if (c.responseDue.isNotBlank()) {
                Text("Response due ${c.responseDue}", color = Color(0xFFB4C4D2), fontSize = 15.sp)
            }
        }

        if (late) WarnBanner("Response overdue", "The respondent has not appeared by ${c.responseDue}.")

        CaseProgressCard(c.status)

        if (canReview) PrimaryButton("Start review", {
            toast(JudgeOps.startReview(c.id, me) ?: "Review started")
        })
        if (canOrder) PrimaryButton("Issue order or direction", { ordering = true })
        if (c.status == CaseStatus.JUDGMENT) {
            BodyText("A judgment has been issued. Close the case to lock the record.")
        }
        if (canClose) OutlineButton("Close case", { closing = true }, Modifier.fillMaxWidth())
        if (canRecuse) {
            OutlineButton("Step aside (recuse)", { recusing = true }, Modifier.fillMaxWidth(), color = CE.Red)
        }

        SectionTitle("Orders")
        if (orders.isEmpty()) {
            EmptyCard("No orders yet", "Orders you issue appear here and are locked.")
        } else {
            orders.forEach { o ->
                CourtCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(o.title, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Pill(o.kind)
                    }
                    BodyText(o.body, Modifier.padding(top = 4.dp))
                    BodyText(
                        "${Store.user(o.judgeId)?.name.orEmpty()} · ${Dates.stamp(o.at)} · Locked",
                        Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        SectionTitle("Filed record")
        InfoBanner("You see filed documents only. Drafts stay private to each side.")
        if (docs.isEmpty()) {
            EmptyCard("Nothing filed", "No documents have been filed in this case.")
        } else {
            docs.forEach { d ->
                CourtCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.name, color = CE.Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Pill(if (d.side == Side.CLAIMANT) "Claimant" else "Respondent", CE.Chip, CE.Navy)
                    }
                    BodyText("${d.type} · ${d.pages} page(s) · ${Dates.stamp(d.at)}", Modifier.padding(top = 4.dp))
                }
            }
        }

        SectionTitle("Private bench notes")
        CourtCard {
            CourtField("New note (only you can see this)", noteText, { noteText = it })
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save note", {
                val r = JudgeOps.addNote(c.id, me, noteText)
                if (r == null) noteText = "" else toast(r)
            })
        }
        notes.forEach { n ->
            CourtCard {
                Text(n.text, color = CE.Navy, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    BodyText(Dates.stamp(n.at), Modifier.weight(1f))
                    TextButton(onClick = { toast(JudgeOps.deleteNote(n.id, me) ?: "Note deleted") }) {
                        Text("Delete", color = CE.Red)
                    }
                }
            }
        }
    }

    if (ordering && c != null) {
        OrderDialog(
            c = c,
            onResult = { message ->
                ordering = false
                toast(message)
            },
            onDismiss = { ordering = false }
        )
    }

    if (closing && c != null) {
        ReasonDialog(
            title = "Close ${c.label}",
            note = "A closing order is issued to both sides and the record becomes read-only.",
            field = "Reason for closing",
            action = "Close case",
            danger = false,
            submit = { reason -> JudgeOps.closeCase(c.id, me, reason) },
            onDone = {
                closing = false
                toast("Case closed")
            },
            onDismiss = { closing = false }
        )
    }

    if (recusing && c != null) {
        ReasonDialog(
            title = "Step aside from ${c.label}",
            note = "The case returns to the registry for a new judge, and both sides are told.",
            field = "Reason (for example, a conflict of interest)",
            action = "Step aside",
            danger = true,
            submit = { reason -> JudgeOps.recuse(c.id, me, reason) },
            onDone = {
                recusing = false
                toast("The registry will allocate a new judge")
                onRecused()
            },
            onDismiss = { recusing = false }
        )
    }
}

@Composable
private fun OrderDialog(c: CaseFile, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val me = Session.userId
    val kinds = if (c.status == CaseStatus.REVIEW) {
        listOf("Direction", "Interim order", "Judgment")
    } else {
        listOf("Direction", "Interim order")
    }
    var kind by remember { mutableStateOf(kinds.first()) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue for ${c.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField("Type", kind, kinds, { kind = it; error = null })
                CourtField("Title", title, { title = it; error = null }, placeholder = "File the written statement")
                CourtArea("Order text", body, { body = it; error = null })
                BodyText("An issued order is locked and sent to both sides. A mistake is corrected by a new order.")
                if (c.status != CaseStatus.REVIEW) {
                    BodyText("A judgment can be issued after you start the review.")
                }
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = JudgeOps.issueOrder(c.id, me, kind, title, body)
                if (r == null) onResult("$kind issued") else error = r
            }) { Text("Issue", color = CE.Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReasonDialog(
    title: String,
    note: String,
    field: String,
    action: String,
    danger: Boolean,
    submit: (String) -> String?,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyText(note)
                CourtArea(field, reason, { reason = it; error = null }, minLines = 3)
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = submit(reason)
                if (r == null) onDone() else error = r
            }) { Text(action, color = if (danger) CE.Red else CE.Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}