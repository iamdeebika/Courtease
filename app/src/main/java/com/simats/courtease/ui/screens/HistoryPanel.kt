package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.OverviewRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.HistoryDto
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.SmallCircle
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun HistorySection(caseId: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<HistoryDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = OverviewRepo.history(caseId)) {
                is ApiResult.Ok -> {
                    data = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(caseId) { load() }

    InfoBanner("Every stage change is listed with its date. Chat, notes, drafts and private material never appear here.")
    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
    OutlineButton(if (loading) "Refreshing..." else "Refresh history", { load() }, Modifier.fillMaxWidth())

    val d = data
    if (d == null) {
        if (error == null) EmptyCard("Loading", "Fetching the history.")
        return
    }
    if (d.incomplete) {
        WarnBanner(
            "Earlier stages are not recorded",
            "This case moved through some stages before history recording was added. Later changes are recorded in full."
        )
    }
    if (d.events.isEmpty()) {
        EmptyCard("No history yet", "Stage changes appear here as the case moves forward.")
        return
    }

    CourtCard {
        d.events.forEachIndexed { index, e ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(top = 5.dp)) { SmallCircle(if (e.recorded) CE.Teal else CE.Muted) }
                Column(Modifier.weight(1f)) {
                    Text(e.text, color = CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val who = if (e.actor.isBlank()) "" else " · ${e.actor}"
                    BodyText(Dates.stamp(e.at) + who, Modifier.padding(top = 2.dp))
                    if (!e.recorded) BodyText("Date taken from the filing record", Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}