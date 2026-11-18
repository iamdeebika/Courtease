package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.FileOps
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Side
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseProgressCard
import com.simats.courtease.ui.components.CaseStatusPill
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.IntegrityBadge
import com.simats.courtease.ui.components.LawLinks
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.UiEmpty
import com.simats.courtease.ui.components.UiField
import com.simats.courtease.ui.components.UiQuote
import com.simats.courtease.ui.components.UiSheet
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
private fun BenchHero(c: CaseFile, claimant: String, respondent: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(CE.Navy, CE.NavyLight, CE.Teal)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BENCH VIEW  \u00B7  " + c.label.uppercase(), color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            CaseStatusPill(c.status)
        }
        Text(c.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
        Text(
            c.caseType.substringAfter("\u00B7 ") + "  |  " + c.court,
            color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp
        )
        if (c.courtroom.isNotBlank()) Text(c.courtroom, color = CE.Gold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(c.claimant + "  vs  " + c.respondent, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("For the claimant: " + claimant, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        Text("For the respondent: " + respondent, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        if (c.responseDue.isNotBlank()) {
            Text("Response due " + c.responseDue, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun BenchStat(value: Int, label: String, modifier: Modifier) {
    CourtCard(modifier.heightIn(min = 88.dp).fillMaxHeight()) {
        Text(value.toString(), color = CE.Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = CE.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun BenchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) CE.Red else CE.Teal
    CourtCard(modifier.heightIn(min = 104.dp).fillMaxHeight(), onClick = onClick) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint) }
        Spacer(Modifier.height(10.dp))
        Text(title, color = if (danger) CE.Red else CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = CE.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BenchFileBadge(ext: String) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(CE.Teal),
        contentAlignment = Alignment.Center
    ) { Text(ext, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun BenchCaseScreen(
    caseId: Int,
    onBack: () -> Unit,
    onRecused: () -> Unit,
    onLaw: () -> Unit = {},
    onSavedLaw: () -> Unit = {},
    onAnalysis: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

        BenchHero(c, claimantLead?.name ?: "-", respondentLead?.name ?: "not yet appeared")

        if (late) WarnBanner("Response overdue", "The respondent has not appeared by ${c.responseDue}.")

        CaseProgressCard(c.status)

        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BenchStat(orders.size, "Orders issued", Modifier.weight(1f))
            BenchStat(docs.size, "Filed documents", Modifier.weight(1f))
        }

        LawLinks(onLaw, onSavedLaw, onAnalysis)

        if (canReview) PrimaryButton("Start review", {
            scope.launch { toast(CaseRepo.startReview(c.id) ?: "Review started") }
        })
        if (canOrder) PrimaryButton("Issue order or direction", { ordering = true })
        if (c.status == CaseStatus.JUDGMENT) {
            BodyText("A judgment has been issued. Close the case to lock the record.")
        }
        if (canClose || canRecuse) {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (canClose) {
                    BenchTile(Icons.Default.Check, "Close case", "Locks the record and tells both sides", Modifier.weight(1f)) {
                        closing = true
                    }
                }
                if (canRecuse) {
                    BenchTile(Icons.Default.Warning, "Step aside", "Return the case to the registry", Modifier.weight(1f), danger = true) {
                        recusing = true
                    }
                }
            }
        }

        SectionTitle("Orders")
        if (orders.isEmpty()) {
            UiEmpty(Icons.Default.Star, "No orders yet", "Orders you issue appear here and are locked.")
        } else {
            orders.forEach { o ->
                CourtCard {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(CE.Navy),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Star, null, tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(o.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            BodyText(
                                Store.user(o.judgeId)?.name.orEmpty() + "  \u00B7  " + Dates.stamp(o.at),
                                Modifier.padding(top = 2.dp)
                            )
                        }
                        Pill(o.kind)
                    }
                    Spacer(Modifier.height(10.dp))
                    UiQuote("ORDER TEXT", o.body)
                    BodyText("Locked. A mistake is corrected by a new order.", Modifier.padding(top = 8.dp))
                }
            }
        }

        SectionTitle("Filed record")
        InfoBanner("You see filed documents only. Drafts stay private to each side.")
        if (docs.isEmpty()) {
            UiEmpty(Icons.Default.Star, "Nothing filed", "No documents have been filed in this case.")
        } else {
            docs.forEach { d ->
                val ext = d.name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "DOC" }
                CourtCard {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BenchFileBadge(ext)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            BodyText(
                                d.type + "  \u00B7  " + d.pages + " page(s)  \u00B7  " + Dates.stamp(d.at),
                                Modifier.padding(top = 2.dp)
                            )
                        }
                        Pill(if (d.side == Side.CLAIMANT) "Claimant" else "Respondent", CE.Chip, CE.Navy)
                    }
                    if (d.hasFile) {
                        IntegrityBadge(d.id, d.filed, d.hasFile)
                        Spacer(Modifier.height(10.dp))
                        OutlineButton("Open", {
                            scope.launch {
                                toast("Opening ${d.name}...")
                                val problem = try {
                                    FileOps.open(context, d)
                                } catch (e: Exception) {
                                    "Couldn't open: ${e.javaClass.simpleName} ${e.message}"
                                }
                                if (problem != null) {
                                    Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
                                }
                            }
                        }, Modifier.fillMaxWidth())
                    } else {
                        BodyText("Sample entry with no file attached", Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        SectionTitle("Private bench notes")
        CourtCard {
            UiField("New note (only you can see this)", noteText, { noteText = it }, lines = 3)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save note", {
                scope.launch {
                    val r = CaseRepo.addNote(c.id, noteText)
                    if (r == null) noteText = "" else toast(r)
                }
            })
        }
        notes.forEach { n ->
            CourtCard {
                UiQuote("YOUR NOTE", n.text, CE.Gold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    BodyText(Dates.stamp(n.at), Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { toast(CaseRepo.deleteNote(n.id) ?: "Note deleted") }
                    }) { Text("Delete", color = CE.Red) }
                }
            }
        }
    }

    if (ordering && c != null) {
        OrderSheet(
            c = c,
            onResult = { message ->
                ordering = false
                toast(message)
            },
            onDismiss = { ordering = false }
        )
    }

    if (closing && c != null) {
        ReasonSheet(
            title = "Close ${c.label}",
            note = "A closing order is issued to both sides and the record becomes read-only.",
            field = "Reason for closing",
            action = "Close case",
            danger = false,
            submit = { reason -> CaseRepo.closeCase(c.id, reason) },
            onDone = {
                closing = false
                toast("Case closed")
            },
            onDismiss = { closing = false }
        )
    }

    if (recusing && c != null) {
        ReasonSheet(
            title = "Step aside from ${c.label}",
            note = "The case returns to the registry for a new judge, and both sides are told.",
            field = "Reason (for example, a conflict of interest)",
            action = "Step aside",
            danger = true,
            submit = { reason -> CaseRepo.recuse(c.id, reason) },
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
private fun OrderSheet(c: CaseFile, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kinds = if (c.status == CaseStatus.REVIEW) {
        listOf("Direction", "Interim order", "Judgment")
    } else {
        listOf("Direction", "Interim order")
    }
    var kind by remember { mutableStateOf(kinds.first()) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    UiSheet(
        title = "Issue for ${c.label}",
        onDismiss = onDismiss,
        subtitle = "An issued order is locked and sent to both sides. A mistake is corrected by a new order."
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { k -> ChoiceChip(k, kind == k) { kind = k; error = null } }
        }
        UiField("Title", title, { title = it; error = null }, placeholder = "File the written statement")
        UiField("Order text", body, { body = it; error = null }, lines = 5)
        if (c.status != CaseStatus.REVIEW) {
            BodyText("A judgment can be issued after you start the review.")
        }
        error?.let { Text(it, color = CE.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        PrimaryButton(if (busy) "Issuing..." else "Issue $kind", {
            if (!busy) {
                busy = true
                scope.launch {
                    val r = CaseRepo.issueOrder(c.id, kind, title, body)
                    busy = false
                    if (r == null) onResult("$kind issued") else error = r
                }
            }
        })
        OutlineButton("Cancel", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReasonSheet(
    title: String,
    note: String,
    field: String,
    action: String,
    danger: Boolean,
    submit: suspend (String) -> String?,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val go: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val r = submit(reason)
                busy = false
                if (r == null) onDone() else error = r
            }
        }
    }

    UiSheet(title = title, onDismiss = onDismiss, subtitle = note) {
        UiField(field, reason, { reason = it; error = null }, lines = 4)
        error?.let { Text(it, color = CE.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        val label = if (busy) "Working..." else action
        if (danger) {
            Button(
                onClick = go,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CE.Red, contentColor = Color.White)
            ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        } else {
            PrimaryButton(label, go)
        }
        OutlineButton("Cancel", onDismiss, Modifier.fillMaxWidth())
    }
}
