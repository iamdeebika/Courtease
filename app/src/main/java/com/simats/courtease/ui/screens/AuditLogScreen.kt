package com.simats.courtease.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.simats.courtease.data.Store
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.theme.CE

@Composable
fun AuditLogScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries = Store.state.audit
        .sortedByDescending { it.at }
        .filter {
            query.isBlank() || it.action.contains(query, true) || it.detail.contains(query, true) ||
                    (Store.user(it.userId)?.name ?: "").contains(query, true)
        }
        .take(150)

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Audit log", "${Store.state.audit.size} entries")
        SearchField(query, { query = it }, "Search action, detail or user")
        if (entries.isEmpty()) EmptyCard("No entries", "Actions will be recorded here.")
        entries.forEach { e ->
            CourtCard {
                Text(e.action, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                BodyText(e.detail, Modifier.padding(top = 2.dp))
                BodyText("${Store.user(e.userId)?.name ?: "System"} · ${Dates.stamp(e.at)}", Modifier.padding(top = 2.dp))
            }
        }
    }
}