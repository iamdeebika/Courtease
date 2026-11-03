package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.ReportDto
import com.simats.courtease.data.api.safeCall
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.components.ThinProgress
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ReportDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        when (val r = safeCall { ApiClient.api.report() }) {
            is ApiResult.Ok -> {
                report = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
    }

    LaunchedEffect(Unit) { load() }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Reports")
        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

        val r = report
        if (r == null) {
            if (error == null) EmptyCard("Loading", "Fetching the latest numbers.")
            return@CourtScaffold
        }

        if (r.overdueResponses > 0) {
            WarnBanner("Response overdue: ${r.overdueResponses}", "The notice period ended with no appearance.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(r.totalCases), "Cases", Modifier.weight(1f))
            StatCard("%02d".format(r.filedLast7Days), "Filed this week", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(r.documentsFiled), "Filed documents", Modifier.weight(1f))
            StatCard("%02d".format(r.ordersIssued), "Orders issued", Modifier.weight(1f))
        }

        SectionTitle("Waiting on the registry")
        CourtCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Account requests", color = CE.Navy, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(r.pendingAccounts.toString(), color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Scrutiny", color = CE.Navy, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(r.awaitingScrutiny.toString(), color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Allocation", color = CE.Navy, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(r.awaitingAllocation.toString(), color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Notice", color = CE.Navy, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(r.awaitingNotice.toString(), color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        SectionTitle("Cases by stage")
        if (r.byStatus.isEmpty()) {
            EmptyCard("No cases yet", "Filed cases appear here.")
        } else {
            CourtCard {
                r.byStatus.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.status.replaceFirstChar { it.uppercase() },
                            color = CE.Navy, fontSize = 16.sp, modifier = Modifier.weight(1f)
                        )
                        Text(s.count.toString(), color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    ThinProgress(s.count.toFloat() / r.totalCases.coerceAtLeast(1))
                }
            }
        }

        SectionTitle("Judge workload")
        if (r.judges.isEmpty()) {
            EmptyCard("No judges", "Approve a judge account first.")
        } else {
            r.judges.forEach { j ->
                CourtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(j.name, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Pill("${j.openCases} open")
                    }
                    BodyText("${j.court} · ${j.courtroom}", Modifier.padding(top = 4.dp))
                    BodyText("${j.closedCases} closed")
                }
            }
        }

        SectionTitle("Users")
        CourtCard {
            BodyText("${r.approvedJudges} judges · ${r.approvedLawyers} advocates approved")
            BodyText("${r.suspendedAccounts} suspended")
        }

        BodyText("Reminders for overdue responses and due tasks are sent automatically every 30 minutes.")
        PrimaryButton(if (busy) "Sending..." else "Send reminders now", {
            if (!busy) {
                busy = true
                scope.launch {
                    val message = when (val x = safeCall { ApiClient.api.runReminders() }) {
                        is ApiResult.Ok -> x.data.message
                        is ApiResult.Err -> x.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    AccountRepo.syncAll()
                    load()
                    busy = false
                }
            }
        })
    }
}