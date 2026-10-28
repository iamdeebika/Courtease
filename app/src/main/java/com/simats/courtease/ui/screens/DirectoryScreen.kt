package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.Role
import com.simats.courtease.model.User
import com.simats.courtease.ui.components.AccountStatusPill
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.theme.CE

@Composable
fun DirectoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<User?>(null) }

    val people = Store.state.users
        .filter { it.role != Role.REGISTRY && it.status != AccountStatus.PENDING }
        .filter {
            filter == "All" || (filter == "Lawyers" && it.role == Role.LAWYER) ||
                    (filter == "Judges" && it.role == Role.JUDGE)
        }
        .filter {
            query.isBlank() || it.name.contains(query, true) || it.registryId.contains(query, true) ||
                    it.email.contains(query, true)
        }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Directory", "${people.size} users")
        SearchField(query, { query = it }, "Search name, ID or email")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Lawyers", "Judges").forEach { f -> ChoiceChip(f, filter == f) { filter = f } }
        }
        if (people.isEmpty()) EmptyCard("No users", "Approved users appear here.")
        people.forEach { u ->
            CourtCard(onClick = { selected = u }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = CE.Navy, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    AccountStatusPill(u.status)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(u.role.label)
                    BodyText(u.registryId.ifBlank { "No ID" })
                }
                Spacer(Modifier.height(6.dp))
                if (u.role == Role.JUDGE) {
                    BodyText("${u.court} · ${u.courtroom} · ${Store.openCasesOf(u.id)} open cases")
                } else {
                    val count = Store.state.cases.count { u.id in it.claimantTeam || u.id in it.respondentTeam }
                    BodyText("${u.council} · $count cases")
                }
            }
        }
    }

    selected?.let { u ->
        UserDialog(
            u = u,
            onResult = { message ->
                selected = null
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun UserDialog(u: User, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val suspended = u.status == AccountStatus.SUSPENDED
    val canChange = u.status == AccountStatus.APPROVED || suspended

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(u.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyText("${u.role.label} · ${u.registryId.ifBlank { "No ID" }}")
                BodyText("${u.email} · ${u.phone}")
                BodyText((if (u.role == Role.LAWYER) "Bar number: " else "Employee ID: ") + u.regNo)
                if (u.role == Role.JUDGE) BodyText("${u.court} · ${u.courtroom}")
                if (u.status == AccountStatus.REJECTED) BodyText("Rejected: ${u.rejectReason}")
                if (suspended) BodyText("Suspended: ${u.rejectReason}")
                if (canChange && !suspended) {
                    CourtField("Reason for suspending", reason, { reason = it; error = null })
                }
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            if (canChange) {
                TextButton(onClick = {
                    val result = Store.setSuspended(u.id, Session.userId, !suspended, reason)
                    if (result != null) error = result
                    else onResult(if (suspended) "Account reinstated" else "Account suspended")
                }) {
                    Text(if (suspended) "Reinstate" else "Suspend", color = if (suspended) CE.Teal else CE.Red)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}