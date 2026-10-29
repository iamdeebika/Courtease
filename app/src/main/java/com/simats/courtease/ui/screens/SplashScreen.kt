package com.simats.courtease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Session
import com.simats.courtease.ui.components.AuthGold
import com.simats.courtease.ui.components.AuthNavyDeep
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        if (Session.userId != 0) {
            // Refresh the account from the server. If the server can't be reached, carry on with what the phone has.
            val valid = withTimeoutOrNull(4000) { AccountRepo.syncAll() } ?: true
            if (!valid) Session.logout(context)
        }
        val wait = 1400 - (System.currentTimeMillis() - started)
        if (wait > 0) delay(wait)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthNavyDeep, CE.Navy, CE.Teal))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(3.dp, AuthGold, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("⚖", color = Color.White, fontSize = 46.sp) }
            Text("CourtEase", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
    }
}