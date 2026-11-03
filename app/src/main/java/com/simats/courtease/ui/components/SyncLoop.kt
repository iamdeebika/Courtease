package com.simats.courtease.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.AccountStatus
import kotlinx.coroutines.delay

// Keeps accounts, cases and alerts fresh, and signs the user out cleanly when the login ends.
@Composable
fun SyncLoop() {
    val context = LocalContext.current
    val id = Session.userId
    val expired = Session.expired

    LaunchedEffect(expired) {
        if (expired) {
            Session.logout(context)
            Store.clearForSignOut()
            Toast.makeText(context, "Your session ended. Please sign in again.", Toast.LENGTH_LONG).show()
            (context as? Activity)?.recreate()
        }
    }

    LaunchedEffect(id) {
        if (id == 0) return@LaunchedEffect
        var wasApproved = Session.user?.status == AccountStatus.APPROVED
        while (true) {
            delay(15_000)
            val valid = AccountRepo.syncAll()
            if (!valid) return@LaunchedEffect // the 401 handler above signs the user out
            val approved = Session.user?.status == AccountStatus.APPROVED
            if (wasApproved && !approved) {
                (context as? Activity)?.recreate()
                return@LaunchedEffect
            }
            wasApproved = approved
        }
    }
}