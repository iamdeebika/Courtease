package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Store
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.COURTS
import com.simats.courtease.model.COURT_ROOMS
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Role
import com.simats.courtease.model.User
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun AccountRequestsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pending = Store.state.users
        .filter { it.status == AccountStatus.PENDING }
        .sortedBy { it.createdAt }
    var selected by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) { AccountRepo.syncAll() }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Account requests", "${pending.size} waiting")
        if (pending.isEmpty()) {
            EmptyCard("All clear", "No accounts are waiting for approval.")
        }
        pending.forEach { u ->
            CourtCard(onClick = { selected = u }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = CE.Navy, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Pill(u.role.label)
                }
                Spacer(Modifier.height(6.dp))
                BodyText("${u.email} · ${u.phone}")
                BodyText(
                    (if (u.role == Role.LAWYER) "Bar no. " else "Employee ID ") + u.regNo + " · " + u.council
                )
                BodyText("Requested ${Dates.stamp(u.createdAt)}", Modifier.padding(top = 2.dp))
            }
        }
    }

    selected?.let { u ->
        AccountDialog(
            u = u,
            onResult = { message ->
                selected = null
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun AccountDialog(u: User, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isJudge = u.role == Role.JUDGE
    var court by remember { mutableStateOf(if (u.council in COURTS) u.council else COURTS.first()) }
    var room by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(u.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BodyText("${u.role.label} · ${u.email}")
                BodyText(u.phone)
                BodyText((if (isJudge) "Employee ID: " else "Bar number: ") + u.regNo)
                BodyText((if (isJudge) "Court of posting: " else "Bar council: ") + u.council)
                if (isJudge) {
                    DropdownField("Court", court, COURTS, { court = it; room = "" })
                    DropdownField("Courtroom", room, COURT_ROOMS[court].orEmpty(), { room = it; error = null })
                }
                CourtField("Reason (needed to reject)", reason, { reason = it; error = null })
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (busy) return@TextButton
                if (isJudge && room.isBlank()) {
                    error = "Choose the judge's court and courtroom"
                    return@TextButton
                }
                busy = true
                scope.launch {
                    val result = AccountRepo.approve(u.id, if (isJudge) court else "", if (isJudge) room else "")
                    busy = false
                    if (result != null) error = result
                    else onResult("Approved · ID ${Store.user(u.id)?.registryId.orEmpty()}")
                }
            }) { Text(if (busy) "Working..." else "Approve", color = CE.Teal) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (busy) return@TextButton
                    busy = true
                    scope.launch {
                        val result = AccountRepo.reject(u.id, reason)
                        busy = false
                        if (result != null) error = result else onResult("Request rejected")
                    }
                }) { Text("Reject", color = CE.Red) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}