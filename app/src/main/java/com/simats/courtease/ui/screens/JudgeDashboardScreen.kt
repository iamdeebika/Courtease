package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.data.Session
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.JudgeCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.components.WarnBanner

@Composable
fun JudgeDashboardScreen(
    onOpenCase: (Int) -> Unit,
    onAllCases: () -> Unit,
    onSignOut: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null
) {
    val me = Session.userId
    val user = Session.user
    var query by remember { mutableStateOf("") }

    val all = JudgeOps.casesOf(me)
    val open = all.filter { it.status != CaseStatus.CLOSED }
    val matching = open.filter {
        query.isBlank() || it.title.contains(query, true) || it.label.contains(query, true) ||
                it.claimant.contains(query, true) || it.respondent.contains(query, true)
    }
    val attention = matching.filter { JudgeOps.needsAttention(it) }
    val rest = matching.filter { it !in attention }
    val overdueCount = open.count { JudgeOps.overdue(it) }

    CourtScaffold(
        actions = {
            if (onNotifications != null) BarIcon(Icons.Default.Notifications, "Alerts", onNotifications)
            if (onProfile != null) BarIcon(Icons.Default.Person, "Profile", onProfile)
            BarIcon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", onSignOut)
        }
    ) {
        ScreenHeader(
            "JUDGE CHAMBERS",
            "${Dates.greeting()}, ${Session.shortName}",
            "${user?.registryId.orEmpty()} · ${user?.courtroom.orEmpty()}, ${user?.court.orEmpty()}"
        )

        SearchField(query, { query = it }, "Search cases before the court")

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(open.size), "Open cases", Modifier.weight(1f))
            StatCard("%02d".format(open.count { JudgeOps.needsAttention(it) }), "Need review", Modifier.weight(1f))
        }

        if (overdueCount > 0) {
            WarnBanner(
                "Response overdue: $overdueCount",
                "The respondent has not appeared. You can start the review."
            )
        }

        SectionTitle("Needs your attention")
        if (attention.isEmpty()) {
            EmptyCard("All clear", "No case is waiting for your review.")
        } else {
            attention.forEach { c -> JudgeCaseCard(c) { onOpenCase(c.id) } }
        }

        SectionTitle("Open cases", "All cases", onAllCases)
        if (rest.isEmpty()) {
            EmptyCard(
                if (all.isEmpty()) "No cases yet" else "Nothing else open",
                if (all.isEmpty()) "Cases appear here when the registry allocates them to you." else "Try another search."
            )
        } else {
            rest.forEach { c -> JudgeCaseCard(c) { onOpenCase(c.id) } }
        }
    }
}