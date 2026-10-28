package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DateField
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.theme.CE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val me = Session.userId
    val c = Store.state.cases.firstOrNull { it.id == caseId }
    val side = c?.let { CaseOps.sideOf(it, me) }

    var tab by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var taskAssignee by remember { mutableStateOf("") }
    var taskDue by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    CourtScaffold(onBack = onBack) {
        if (c == null || side == null) {
            EmptyCard("Not available", "This case may have been removed, or you are not on it.")
            return@CourtScaffold
        }
        val members = CaseOps.teamOf(c, side).mapNotNull { Store.user(it) }
        val lead = CaseOps.isLead(c, me)

        ScreenHeader(
            "TEAM WORKSPACE", c.label,
            if (side == Side.CLAIMANT) "Claimant's side · private to your team" else "Respondent's side · private to your team"
        )

        val tabs = listOf("Chat", "Notes", "Tasks", "Team")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Track).padding(6.dp)) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (tab == i) Color.White else Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, fontSize = 15.sp, textAlign = TextAlign.Center,
                        color = if (tab == i) CE.Teal else CE.Muted,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        when (tab) {
            // ---------- chat ----------
            0 -> {
                val messages = Store.state.messages
                    .filter { it.caseId == c.id && it.side == side }
                    .sortedBy { it.at }
                if (messages.isEmpty()) EmptyCard("No messages yet", "Start the conversation with your team.")
                messages.forEach { m ->
                    val mine = m.userId == me
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (mine) CE.Teal else CE.Chip).padding(12.dp)
                        ) {
                            if (!mine) {
                                Text(
                                    Store.user(m.userId)?.name.orEmpty(), color = CE.Teal,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(m.text, color = if (mine) Color.White else CE.Navy, fontSize = 16.sp)
                            Text(
                                Dates.stamp(m.at), fontSize = 11.sp,
                                color = if (mine) Color(0xFFCFE3E1) else CE.Muted
                            )
                        }
                    }
                }
                CourtField(
                    "Message", draft, { draft = it },
                    trailing = {
                        TextButton(onClick = {
                            val r = CaseOps.sendMessage(c.id, me, draft)
                            if (r == null) draft = "" else toast(r)
                        }) { Text("Send", color = CE.Teal, fontWeight = FontWeight.Bold) }
                    }
                )
            }

            // ---------- notes ----------
            1 -> {
                CourtCard {
                    CourtField("New note", noteText, { noteText = it })
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Add note", {
                        val r = CaseOps.addNote(c.id, me, noteText)
                        if (r == null) noteText = "" else toast(r)
                    })
                }
                val notes = Store.state.notes
                    .filter { it.caseId == c.id && it.side == side }
                    .sortedByDescending { it.at }
                SectionTitle("Shared notes")
                if (notes.isEmpty()) EmptyCard("No notes yet", "Notes here are shared with your team only.")
                notes.forEach { n ->
                    CourtCard {
                        Text(n.text, color = CE.Navy, fontSize = 16.sp)
                        BodyText(
                            "${Store.user(n.userId)?.name.orEmpty()} · ${Dates.stamp(n.at)}",
                            Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ---------- tasks ----------
            2 -> {
                val byName = members.associate { it.name to it.id }
                CourtCard {
                    CourtField("Task", taskTitle, { taskTitle = it })
                    Spacer(Modifier.height(12.dp))
                    DropdownField(
                        "Assign to", taskAssignee.ifBlank { members.firstOrNull()?.name.orEmpty() },
                        byName.keys.toList(), { taskAssignee = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    DateField("Due date", taskDue, { showPicker = true })
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Add task", {
                        val assignee = byName[taskAssignee.ifBlank { members.firstOrNull()?.name.orEmpty() }] ?: 0
                        val r = CaseOps.addTask(c.id, me, assignee, taskTitle, taskDue)
                        if (r == null) {
                            taskTitle = ""
                            taskDue = ""
                            toast("Task added")
                        } else {
                            toast(r)
                        }
                    })
                }
                val tasks = Store.state.tasks
                    .filter { it.caseId == c.id && it.side == side }
                    .sortedBy { it.done }
                SectionTitle("${tasks.size} task(s)")
                if (tasks.isEmpty()) EmptyCard("No tasks yet", "Assign work to your team above.")
                tasks.forEach { t ->
                    CourtCard(onClick = {
                        val r = CaseOps.toggleTask(t.id, me)
                        if (r != null) toast(r)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.done, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = CE.Teal)
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    t.title, fontSize = 17.sp,
                                    color = if (t.done) CE.Muted else CE.Navy,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                                )
                                BodyText("${Store.user(t.assigneeId)?.name.orEmpty()} · due ${t.due}")
                            }
                        }
                    }
                }
            }

            // ---------- team ----------
            else -> {
                SectionTitle("Advocates on this side")
                members.forEachIndexed { index, u ->
                    CourtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Pill(if (index == 0) "Lead" else "Associate")
                        }
                        BodyText("${u.registryId} · ${u.email}", Modifier.padding(top = 4.dp))
                    }
                }

                val pending = Store.state.invites.filter { it.caseId == c.id && it.side == side && it.status == "pending" }
                if (pending.isNotEmpty()) {
                    SectionTitle("Pending invitations")
                    pending.forEach { BodyText("${it.email} · waiting for a reply") }
                }

                if (lead) {
                    SectionTitle("Invite an advocate")
                    CourtCard {
                        CourtField("Email", email, { email = it })
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Send invitation", {
                            val r = CaseOps.invite(c.id, me, email)
                            if (r == null) {
                                email = ""
                                toast("Invitation sent")
                            } else {
                                toast(r)
                            }
                        })
                    }
                } else {
                    BodyText("Only the lead advocate can invite people to the team.")
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { taskDue = Dates.formatUtc(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}