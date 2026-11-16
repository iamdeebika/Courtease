package com.simats.courtease.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.OverviewRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.UpcomingDto
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay

// Response dates and task dates on a dashboard. Tapping an item opens its case when onOpenCase is given.
@Composable
fun UpcomingCard(onOpenCase: ((Int) -> Unit)? = null) {
    var data by remember { mutableStateOf<UpcomingDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            when (val r = OverviewRepo.upcoming()) {
                is ApiResult.Ok -> {
                    data = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
            delay(60_000)
        }
    }

    SectionTitle("Upcoming")
    val d = data
    if (d == null) {
        if (error != null) BodyText("Couldn't load deadlines: $error") else EmptyCard("Loading", "Checking deadlines.")
        return
    }
    if (d.items.isEmpty()) {
        EmptyCard("Nothing due", "Response dates and task dates appear here.")
        return
    }

    CourtCard {
        if (d.overdue > 0) {
            Text(
                "${d.overdue} overdue", color = CE.Red, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        val shown = if (expanded) d.items else d.items.take(4)
        shown.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            val tint = when {
                item.days < 0 -> CE.Red
                item.days <= 2 -> CE.AmberText
                else -> CE.Teal
            }
            val open = if (onOpenCase != null) Modifier.clickable { onOpenCase(item.caseId) } else Modifier
            Column(Modifier.fillMaxWidth().then(open)) {
                Text(item.title, color = CE.Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                BodyText(item.detail, Modifier.padding(top = 2.dp))
                Text(
                    "${item.dueText} · ${item.dateText}", color = tint, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (d.items.size > 4) {
            Spacer(Modifier.height(14.dp))
            OutlineButton(
                if (expanded) "Show fewer" else "Show all (${d.total})",
                { expanded = !expanded }, Modifier.fillMaxWidth()
            )
        }
    }
}