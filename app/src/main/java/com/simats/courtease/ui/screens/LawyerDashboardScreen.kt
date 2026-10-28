package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.Dates
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.LawyerCaseCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.theme.CE

@Composable
fun LawyerDashboardScreen(
    onOpenCase: (Int) -> Unit,
    onFileCase: () -> Unit,
    onJoinCase: () -> Unit,
    onSignOut: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val me = Session.userId
    val user = Session.user
    var query by remember { mutableStateOf("") }

    val all = CaseOps.casesOf(me)
    val invites = if (user != null) CaseOps.pendingInvites(user) else emptyList()
    val matching = all.filter {
        query.isBlank() || it.title.contains(query, true) || it.label.contains(query, true) ||
                it.claimant.contains(query, true) || it.respondent.contains(query, true)
    }
    val needAction = matching.filter { CaseOps.editable(it) && CaseOps.isLead(it, me) }
    val others = matching.filter { it !in needAction }

    CourtScaffold(
        actions = {
            if (onNotifications != null) BarIcon(Icons.Default.Notifications, "Alerts", onNotifications)
            if (onProfile != null) BarIcon(Icons.Default.Person, "Profile", onProfile)
            BarIcon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", onSignOut)
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = onFileCase,
                containerColor = CE.Teal,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("File a case") }
            )
        },
        bottomSpace = 96.dp
    ) {
        ScreenHeader(
            "ADVOCATE WORKSPACE",
            "${Dates.greeting()}, ${Session.shortName}",
            "${user?.registryId.orEmpty()} · ${Dates.todayLong()}"
        )

        SearchField(query, { query = it }, "Search my cases")

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(all.size), "My cases", Modifier.weight(1f))
            StatCard("%02d".format(needAction.size + invites.size), "Need action", Modifier.weight(1f))
        }

        if (invites.isNotEmpty()) {
            SectionTitle("Invitations")
            invites.forEach { inv ->
                val c = Store.caseById(inv.caseId)
                val from = Store.user(inv.invitedBy)?.name.orEmpty()
                CourtCard {
                    Text(c?.title ?: "Case", color = CE.Navy, fontSize = 19.sp)
                    BodyText(
                        "$from invited you to join the ${inv.side.name.lowercase()}'s team",
                        Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlineButton("Decline", {
                            val r = CaseOps.respondInvite(inv.id, me, false)
                            Toast.makeText(context, r ?: "Invitation declined", Toast.LENGTH_SHORT).show()
                        }, Modifier.weight(1f))
                        PrimaryButton("Accept", {
                            val r = CaseOps.respondInvite(inv.id, me, true)
                            Toast.makeText(context, r ?: "You joined the team", Toast.LENGTH_SHORT).show()
                        }, Modifier.weight(1f))
                    }
                }
            }
        }

        if (needAction.isNotEmpty()) {
            SectionTitle("Needs your action")
            needAction.forEach { c -> LawyerCaseCard(c, me) { onOpenCase(c.id) } }
        }

        SectionTitle("My cases")
        if (others.isEmpty()) {
            EmptyCard(
                if (all.isEmpty()) "No cases yet" else "Nothing here",
                if (all.isEmpty()) "Tap File a case, or join one with a court notice code." else "Try another search."
            )
        } else {
            others.forEach { c -> LawyerCaseCard(c, me) { onOpenCase(c.id) } }
        }

        OutlineButton("Join a case with code", onJoinCase, Modifier.fillMaxWidth())
    }
}