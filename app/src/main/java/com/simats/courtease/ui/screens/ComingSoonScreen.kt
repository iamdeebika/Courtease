package com.simats.courtease.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import com.simats.courtease.data.Session
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.CourtScaffold

@Composable
fun ComingSoonScreen(onSignOut: () -> Unit) {
    val user = Session.user
    CourtScaffold(actions = { BarIcon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", onSignOut) }) {
        ScreenHeader(user?.role?.label?.uppercase().orEmpty(), user?.name.orEmpty(), user?.registryId)
        EmptyCard("Dashboard coming next", "This workspace is built in a later part.")
    }
}