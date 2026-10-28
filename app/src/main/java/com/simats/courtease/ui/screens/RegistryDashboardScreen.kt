package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.components.ToolRow
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE

@Composable
fun RegistryDashboardScreen(
    onAccounts: () -> Unit,
    onDirectory: () -> Unit,
    onSignOut: () -> Unit,
    onQueue: () -> Unit = {},
    onAllocation: () -> Unit = {},
    onNotices: () -> Unit = {},
    onAudit: () -> Unit = {},
    onNotifications: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = Store.state
    val pendingAccounts = s.users.count { it.status == AccountStatus.PENDING }
    val approved = s.users.count { it.status == AccountStatus.APPROVED && it.role != Role.REGISTRY }
    val filed = s.cases.count { it.status == CaseStatus.FILED }
    val registered = s.cases.count { it.status == CaseStatus.REGISTERED }
    val toNotice = s.cases.count { it.status == CaseStatus.ALLOCATED }
    val overdue = Store.overdue().size
    var confirmReset by remember { mutableStateOf(false) }

    CourtScaffold(
        actions = {
            if (onNotifications != null) BarIcon(Icons.Default.Notifications, "Alerts", onNotifications)
            if (onProfile != null) BarIcon(Icons.Default.Person, "Profile", onProfile)
            BarIcon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", onSignOut)
        }
    ) {
        ScreenHeader("COURT REGISTRY", "${Dates.greeting()}, ${Session.shortName}", Dates.todayLong())

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(pendingAccounts), "Account requests", Modifier.weight(1f))
            StatCard("%02d".format(filed), "Pending scrutiny", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("%02d".format(registered), "To allocate", Modifier.weight(1f))
            StatCard("%02d".format(toNotice), "To issue notice", Modifier.weight(1f))
        }

        if (overdue > 0) {
            WarnBanner("Response overdue: $overdue", "The notice period ended with no appearance.")
        }

        SectionTitle("Registry work")
        ToolRow(Icons.Default.Person, "Account requests", "$pendingAccounts waiting", onAccounts)
        ToolRow(Icons.AutoMirrored.Filled.List, "Directory", "$approved approved users", onDirectory)
        ToolRow(Icons.Default.Create, "Filing queue", "$filed to scrutinise", onQueue)
        ToolRow(Icons.Default.LocationOn, "Allocation", "$registered awaiting a judge", onAllocation)
        ToolRow(Icons.Default.Email, "Notices", "$toNotice ready", onNotices)
        ToolRow(Icons.Default.Info, "Audit log", "${s.audit.size} entries", onAudit)

        OutlineButton(
            "Reset demo data", { confirmReset = true },
            Modifier.fillMaxWidth(), color = CE.Red
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data?") },
            text = { Text("All accounts, cases and logs return to the starting demo. Accounts you created are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    Store.resetDemo()
                    Toast.makeText(context, "Demo data reset", Toast.LENGTH_SHORT).show()
                }) { Text("Reset", color = CE.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}