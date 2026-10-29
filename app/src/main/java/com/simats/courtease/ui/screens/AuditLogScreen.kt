package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Store
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.AuditDto
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.theme.CE

@Composable
fun AuditLogScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var server by remember { mutableStateOf<List<AuditDto>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = AccountRepo.audit()) {
            is ApiResult.Ok -> server = r.data
            is ApiResult.Err -> loadError = r.message
        }
    }

    val accounts = server.filter {
        query.isBlank() || it.action.contains(query, true) || it.detail.contains(query, true) ||
                it.userName.contains(query, true)
    }
    val cases = Store.state.audit
        .sortedByDescending { it.at }
        .filter {
            query.isBlank() || it.action.contains(query, true) || it.detail.contains(query, true) ||
                    (Store.user(it.userId)?.name ?: "").contains(query, true)
        }
        .take(150)

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Audit log")
        SearchField(query, { query = it }, "Search action, detail or user")

        SectionTitle("Account events")
        loadError?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
        if (accounts.isEmpty() && loadError == null) EmptyCard("No entries", "Account actions are recorded here.")
        accounts.forEach { e ->
            CourtCard {
                Text(e.action, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                BodyText(e.detail, Modifier.padding(top = 2.dp))
                BodyText("${e.userName} · ${Dates.stamp(e.createdAt)}", Modifier.padding(top = 2.dp))
            }
        }

        SectionTitle("Case events (this phone)")
        if (cases.isEmpty()) EmptyCard("No entries", "Case actions are recorded here.")
        cases.forEach { e ->
            CourtCard {
                Text(e.action, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                BodyText(e.detail, Modifier.padding(top = 2.dp))
                BodyText("${Store.user(e.userId)?.name ?: "System"} · ${Dates.stamp(e.at)}", Modifier.padding(top = 2.dp))
            }
        }
    }
}