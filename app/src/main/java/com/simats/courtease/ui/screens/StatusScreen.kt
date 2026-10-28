package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Session
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.ui.components.AuthScaffold
import com.simats.courtease.ui.components.GradientButton
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.theme.CE

// Shown after sign-in when the account is not approved (or has been suspended).
@Composable
fun StatusScreen(onContinue: () -> Unit, onReapply: () -> Unit, onSignOut: () -> Unit) {
    val user = Session.user
    val status = user?.status ?: AccountStatus.PENDING

    val icon: String
    val title: String
    val message: String
    when (status) {
        AccountStatus.PENDING -> {
            icon = "⏳"; title = "Awaiting approval"
            message = "The court registry is reviewing your request."
        }
        AccountStatus.APPROVED -> {
            icon = "✅"; title = "Account approved"
            message = "Your registry ID: ${user?.registryId.orEmpty()}"
        }
        AccountStatus.REJECTED -> {
            icon = "⚠️"; title = "Request rejected"
            message = "Reason: ${user?.rejectReason.orEmpty()}"
        }
        AccountStatus.SUSPENDED -> {
            icon = "⛔"; title = "Account suspended"
            message = "Reason: ${user?.rejectReason.orEmpty()}. Contact the registry."
        }
    }

    AuthScaffold(title = title) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(icon, fontSize = 56.sp)
            Text(message, color = CE.Muted, fontSize = 17.sp, textAlign = TextAlign.Center)
        }
        if (status == AccountStatus.APPROVED) GradientButton("Continue") { onContinue() }
        if (status == AccountStatus.REJECTED) GradientButton("Re-apply") { onReapply() }
        OutlineButton("Sign out", onSignOut, Modifier.fillMaxWidth())
    }
}