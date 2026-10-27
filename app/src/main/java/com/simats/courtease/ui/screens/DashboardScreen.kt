package com.simats.courtease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.data.Session
import com.simats.courtease.model.Dates
import com.simats.courtease.model.DemoData
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CaseCard
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.theme.CE

@Composable
fun DashboardScreen(
    onOpenCase: (String) -> Unit,
    onViewAll: () -> Unit,
    onCreate: () -> Unit,
    onNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    val cases by CaseRepository.cases.collectAsState()
    var query by remember { mutableStateOf("") }
    val role = Session.role

    val filtered = cases.filter {
        query.isBlank() || it.title.contains(query, true) || it.caseNo.contains(query, true) ||
                it.caseType.contains(query, true)
    }
    val shown = if (query.isBlank()) filtered.take(3) else filtered
    val upcoming = Dates.nextUpcoming(cases)

    CourtScaffold(
        actions = {
            BarIcon(Icons.Default.Notifications, "Notifications", onNotifications)
            BarIcon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", onLogout)
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = CE.Teal,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New case") }
            )
        },
        bottomSpace = 96.dp
    ) {
        ScreenHeader(
            "02 / ${role.workspace}",
            "${Dates.greeting()}, ${Session.name}",
            "${role.designation} · ${Dates.todayLong()}"
        )

        SearchField(
            query, { query = it },
            if (role.name == "JUDGE") "Search cases before the court" else "Search assigned cases"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(cases.size), "Active cases", Modifier.weight(1f))
            StatCard("%02d".format(DemoData.reminders.size), "Pending actions", Modifier.weight(1f))
        }

        if (upcoming != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CE.Navy)
                    .clickable { onOpenCase(upcoming.caseNo) }
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("UPCOMING HEARING", color = Color(0xFF9FD0C8), fontSize = 14.sp)
                Text(upcoming.nextHearing, color = Color.White, fontSize = 28.sp)
                Text("${upcoming.caseNo} · ${upcoming.caseType.substringAfter("· ")}", color = Color.White, fontSize = 17.sp)
                Text(
                    "${upcoming.court.substringBefore(",")} · Hearing preparation pending",
                    color = Color(0xFFB4C4D2), fontSize = 15.sp
                )
            }
        }

        SectionTitle(
            if (role.name == "JUDGE") "Cases before the court" else "Assigned cases",
            "View all ${cases.size}", onViewAll
        )
        if (shown.isEmpty()) {
            EmptyCard(
                if (cases.isEmpty()) "No cases yet" else "No matching cases",
                if (cases.isEmpty()) "Tap New case to create your first workspace." else "Try a different search."
            )
        } else {
            shown.forEach { c -> CaseCard(c) { onOpenCase(c.caseNo) } }
        }

        SectionTitle("Actions & reminders", "View all", onNotifications)
        DemoData.reminders.take(3).forEach { r ->
            CourtCard(onClick = onNotifications) {
                Text(r.title, color = CE.Navy, fontSize = 18.sp)
                BodyText(r.detail, Modifier.padding(top = 4.dp))
            }
        }
    }
}