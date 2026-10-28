package com.simats.courtease.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.ui.theme.CE

@Composable
fun AccountStatusPill(status: AccountStatus) {
    when (status) {
        AccountStatus.APPROVED -> Pill("Approved", CE.Mint, CE.Teal)
        AccountStatus.PENDING -> Pill("Pending", CE.Amber, CE.AmberText)
        AccountStatus.REJECTED -> Pill("Rejected", Color(0xFFFCE8E6), CE.Red)
        AccountStatus.SUSPENDED -> Pill("Suspended", CE.Chip, CE.Muted)
    }
}

@Composable
fun CaseStatusPill(status: CaseStatus) {
    when (status) {
        CaseStatus.DRAFT -> Pill(status.label, CE.Chip, CE.Muted)
        CaseStatus.FILED -> Pill(status.label, CE.Amber, CE.AmberText)
        CaseStatus.RETURNED -> Pill(status.label, Color(0xFFFCE8E6), CE.Red)
        CaseStatus.CLOSED -> Pill(status.label, CE.Chip, CE.Navy)
        else -> Pill(status.label, CE.Mint, CE.Teal)
    }
}