package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CommonOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SmallCircle
import com.simats.courtease.ui.theme.CE

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val me = Session.userId
    val items = CommonOps.notifsOf(me)
    val unread = items.count { !it.read }

    CourtScaffold(onBack = onBack) {
        ScreenHeader(
            "ALERTS", "Notifications",
            if (unread == 0) "You're all caught up" else "$unread unread"
        )

        if (unread > 0) {
            OutlineButton("Mark all as read", { CommonOps.markAllRead(me) }, Modifier.fillMaxWidth())
        }

        if (items.isEmpty()) {
            EmptyCard("Nothing yet", "Updates about your cases and account appear here.")
        }

        items.forEach { n ->
            val openable = n.caseId != 0 && Store.caseById(n.caseId) != null
            CourtCard(
                color = if (n.read) Color.White else CE.Mint,
                borderColor = if (n.read) CE.Border else CE.Teal,
                onClick = {
                    CommonOps.markRead(n.id)
                    if (openable) onOpen(n.caseId)
                }
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title, color = CE.Navy, fontSize = 18.sp,
                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!n.read) SmallCircle(CE.Teal, 10.dp)
                }
                BodyText(n.body, Modifier.padding(top = 4.dp))
                BodyText(
                    Dates.stamp(n.at) + if (openable) " · Tap to open" else "",
                    Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}