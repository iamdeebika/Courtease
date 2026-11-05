package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.PapersRepo
import com.simats.courtease.data.ReportOps
import com.simats.courtease.data.Store
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.RegistryCaseCard
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun NoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cases = Store.state.cases
    val ready = cases.filter { it.status == CaseStatus.ALLOCATED }
    val issued = cases.filter { it.caseCode.isNotBlank() }.sortedByDescending { it.id }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun noticePdf(caseId: Int) {
        scope.launch {
            when (val r = PapersRepo.noticeBytes(caseId)) {
                is ApiResult.Ok -> {
                    val file = PapersRepo.save(context, "CourtEase_Notice_$caseId.pdf", r.data)
                    ReportOps.view(context, file)?.let { toast(it) }
                }
                is ApiResult.Err -> toast(r.message)
            }
        }
    }

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
                        scope.launch {
                            val result = CaseRepo.issueNotice(c.id)
                            if (result == null) toast("Notice issued · code ${Store.caseById(c.id)?.caseCode}")
                            else toast(result)
                        }
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
                actions = {
                    OutlineButton("Notice PDF", { noticePdf(c.id) }, Modifier.fillMaxWidth())
                    if (!appeared) {
                        Spacer(Modifier.height(8.dp))
                        OutlineButton("Reissue code", {
                            scope.launch {
                                val result = CaseRepo.reissueCode(c.id)
                                if (result == null) toast("New code ${Store.caseById(c.id)?.caseCode}") else toast(result)
                            }
                        }, Modifier.fillMaxWidth())
                    }
                }
            )
        }
    }
}