package com.simats.courtease.ui.screens

import androidx.compose.runtime.Composable
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.RegistryCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle

@Composable
fun FilingQueueScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val cases = Store.state.cases
    val filed = cases.filter { it.status == CaseStatus.FILED }.sortedBy { it.filedAt }
    val returned = cases.filter { it.status == CaseStatus.RETURNED }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Filing queue", "${filed.size} to scrutinise")

        SectionTitle("Pending scrutiny")
        if (filed.isEmpty()) EmptyCard("All clear", "No filings are waiting.")
        filed.forEach { c ->
            RegistryCaseCard(c, detail = "Filed ${Dates.stamp(c.filedAt)}", onClick = { onOpen(c.id) })
        }

        SectionTitle("Returned to lawyers")
        if (returned.isEmpty()) EmptyCard("None", "No case is waiting for the lawyer to correct it.")
        returned.forEach { c ->
            RegistryCaseCard(c, detail = "${c.defects.size} defect(s)", onClick = { onOpen(c.id) })
        }
    }
}