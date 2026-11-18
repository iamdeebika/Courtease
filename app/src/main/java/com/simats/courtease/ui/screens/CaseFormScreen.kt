package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.model.CASE_TYPES
import com.simats.courtease.model.COURTS
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.VSpace
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

// caseId 0 means a new case. Saving goes to the server first, and the screen moves on only after the server accepted it.
@Composable
fun CaseFormScreen(caseId: Int, onBack: () -> Unit, onSaved: (Int) -> Unit) {
    val me = Session.userId
    val scope = rememberCoroutineScope()
    val existing = remember { Store.caseById(caseId) }
    val editing = existing != null
    val locked = existing != null && (!CaseOps.editable(existing) || existing.claimantTeam.firstOrNull() != me)

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.caseType ?: CASE_TYPES.first()) }
    var court by remember { mutableStateOf(existing?.court ?: COURTS.first()) }
    var claimant by remember { mutableStateOf(existing?.claimant ?: "") }
    var respondent by remember { mutableStateOf(existing?.respondent ?: "") }
    var fee by remember { mutableStateOf(existing?.feeAmount ?: "") }
    var receipt by remember { mutableStateOf(existing?.feeReceipt ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    CourtScaffold(onBack = onBack) {
        if (locked) {
            EmptyCard("Can't edit", "Only the lead advocate can edit a draft or returned case.")
            return@CourtScaffold
        }
        ScreenHeader(if (editing) "EDIT CASE" else "NEW FILING", if (editing) "Edit case" else "File a case")

        CourtField("Case title", title, { title = it; error = null }, placeholder = "Ravi vs Kumar Traders")
        DropdownField("Case type", type, CASE_TYPES, { type = it })
        DropdownField("Filing court", court, COURTS, { court = it })

        CourtCard {
            Text("Parties", color = CE.Navy, fontSize = 19.sp)
            VSpace(12)
            CourtField("Claimant (your client)", claimant, { claimant = it; error = null })
            VSpace(12)
            CourtField("Respondent", respondent, { respondent = it; error = null })
        }

        CourtCard {
            Text("Court fee", color = CE.Navy, fontSize = 19.sp)
            VSpace(12)
            CourtField(
                "Amount (\u20B9)", fee, { fee = it.filter { c -> c.isDigit() || c == ',' }; error = null },
                keyboardType = KeyboardType.Number
            )
            VSpace(12)
            CourtField("Receipt number", receipt, { receipt = it; error = null }, placeholder = "RC-12345")
        }

        error?.let { Text(it, color = CE.Red, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }

        PrimaryButton(
            if (busy) "Saving..." else if (editing) "Save changes" else "Save draft",
            onClick = {
                val problem = when {
                    busy -> "Please wait"
                    title.isBlank() -> "Enter the case title"
                    claimant.isBlank() || respondent.isBlank() -> "Enter both parties"
                    else -> null
                }
                if (problem != null) {
                    if (!busy) error = problem
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        if (existing != null) {
                            val result = CaseRepo.updateDraft(
                                existing.id, title, type, court, claimant, respondent, fee, receipt
                            )
                            busy = false
                            if (result == null) onSaved(existing.id) else error = result
                        } else {
                            when (val r = CaseRepo.createDraft(title, type, court, claimant, respondent, fee, receipt)) {
                                is ApiResult.Ok -> {
                                    busy = false
                                    onSaved(r.data)
                                }
                                is ApiResult.Err -> {
                                    busy = false
                                    error = r.message
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
