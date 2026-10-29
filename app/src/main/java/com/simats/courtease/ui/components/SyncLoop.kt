package com.simats.courtease.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Session
import com.simats.courtease.model.AccountStatus
import kotlinx.coroutines.delay

@Composable
fun SyncLoop() {
    val context = LocalContext.current
    val id = Session.userId
    LaunchedEffect(id) {
        if (id == 0) return@LaunchedEffect
        var wasApproved = Session.user?.status == AccountStatus.APPROVED
        while (true) {
            delay(15_000)
            val valid = AccountRepo.syncAll()
            if (!valid) {
                Session.logout(context)
                (context as? Activity)?.recreate()
                return@LaunchedEffect
            }
            val approved = Session.user?.status == AccountStatus.APPROVED
            if (wasApproved && !approved) {
                (context as? Activity)?.recreate()
                return@LaunchedEffect
            }
            wasApproved = approved
        }
    }
}