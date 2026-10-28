package com.simats.courtease.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.label
import com.simats.courtease.ui.theme.CE

// One case in a registry list: number, status, title, court, who filed it, plus optional actions.
@Composable
fun RegistryCaseCard(
    c: CaseFile,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val lawyer = Store.user(c.claimantTeam.firstOrNull() ?: 0)?.name ?: "Unknown"
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.label, color = CE.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            CaseStatusPill(c.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(c.title, color = CE.Navy, fontSize = 20.sp)
        BodyText("${c.caseType.substringAfter("· ")} · ${c.court}", Modifier.padding(top = 4.dp))
        BodyText("Filed by $lawyer")
        if (detail != null) {
            Text(
                detail, color = CE.Teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}