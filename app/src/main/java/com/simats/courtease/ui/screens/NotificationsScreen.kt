package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.model.DemoData
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.theme.CE

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    CourtScaffold(onBack = onBack) {
        ScreenHeader("ALERTS", "Actions & reminders", "Hearings, deadlines and status updates.")
        InfoBanner("Sample reminders. Push alerts for hearings and deadlines are added with the notification service.")
        DemoData.reminders.forEach { r ->
            CourtCard {
                Text(r.title, color = CE.Navy, fontSize = 18.sp)
                BodyText(r.detail, Modifier.padding(top = 4.dp))
            }
        }
    }
}