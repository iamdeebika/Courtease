package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.RegistryCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle

@Composable
fun NoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cases = Store.state.cases
    val ready = cases.filter { it.status == CaseStatus.ALLOCATED }
    val issued = cases.filter { it.caseCode.isNotBlank() }.sortedByDescending { it.id }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    CourtScaffold(onBack = onBack) {
        ScreenHeader("REGISTRY", "Notices", "${ready.size} ready to issue")

        SectionTitle("Ready to issue")
        if (ready.isEmpty()) EmptyCard("All clear", "Allocated cases appear here.")
        ready.forEach { c ->
            val judge = Store.user(c.judgeId)
            RegistryCaseCard(
                c,
                detail = "Before ${judge?.name.orEmpty()} · ${c.courtroom}",
                actions = {
                    PrimaryButton("Issue notice", {
                        val result = Store.issueNotice(c.id, Session.userId)
                        if (result == null) {
                            val code = Store.caseById(c.id)?.caseCode
                            toast("Notice issued · code $code")
                        } else toast(result)
                    })
                }
            )
        }

        SectionTitle("Notices issued")
        if (issued.isEmpty()) EmptyCard("None yet", "Issued notices appear here.")
        issued.forEach { c ->
            val appeared = c.respondentTeam.isNotEmpty()
            val late = !appeared && Dates.isPast(c.responseDue)
            val state = when {
                appeared -> "Respondent has appeared"
                late -> "Response overdue"
                else -> "Awaiting respondent"
            }
            RegistryCaseCard(
                c,
                detail = "Code ${c.caseCode} · due ${c.responseDue} · $state",
                actions = if (!appeared) ({
                    OutlineButton("Reissue code", {
                        val result = Store.reissueCode(c.id, Session.userId)
                        if (result == null) toast("New code ${Store.caseById(c.id)?.caseCode}") else toast(result)
                    }, Modifier.fillMaxWidth())
                }) else null
            )
        }
    }
}