package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.model.CASE_TYPES
import com.simats.courtease.model.COURTS
import com.simats.courtease.model.CivilCase
import com.simats.courtease.model.Dates
import com.simats.courtease.model.STAGES
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.DateField
import com.simats.courtease.ui.components.DropdownField
import com.simats.courtease.ui.components.FieldLabel
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.VSpace
import com.simats.courtease.ui.theme.CE

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCaseScreen(caseNo: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val existing = remember { caseNo?.let { CaseRepository.get(it) } }
    val editing = existing != null

    var no by remember { mutableStateOf(existing?.caseNo ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.caseType ?: CASE_TYPES.first()) }
    var claimant by remember { mutableStateOf(existing?.claimant ?: "") }
    var respondent by remember { mutableStateOf(existing?.respondent ?: "") }
    var court by remember { mutableStateOf(existing?.court ?: COURTS.first()) }
    var stage by remember { mutableStateOf(existing?.stage ?: STAGES.first()) }
    var hearing by remember { mutableStateOf(existing?.nextHearing ?: "") }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CourtScaffold(onBack = onBack) {
        ScreenHeader(
            if (editing) "EDIT CASE" else "03 / NEW CASE DRAFT",
            if (editing) "Edit case" else "Create a case",
            if (editing) "Update the case details." else "Start a new workspace. Details can be reviewed before creation."
        )
        if (!editing) Pill("Unsaved draft")

        CourtField("Case number", no, { no = it; error = null }, enabled = !editing, placeholder = "CE-2026-0170")
        CourtField("Case title", title, { title = it; error = null }, placeholder = "Commercial lease dispute")
        DropdownField("Case type", type, CASE_TYPES, { type = it })

        CourtCard {
            Text("Parties", color = CE.Navy, fontSize = 19.sp)
            VSpace(12)
            CourtField("Claimant / applicant", claimant, { claimant = it; error = null })
            VSpace(12)
            CourtField("Respondent", respondent, { respondent = it; error = null })
        }

        DropdownField("Court", court, COURTS, { court = it })

        androidx.compose.foundation.layout.Column {
            FieldLabel("Current stage")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STAGES.forEach { s -> ChoiceChip(s, stage == s) { stage = s } }
            }
        }

        DateField("Next hearing", hearing, { showPicker = true })

        error?.let { Text(it, color = CE.Red, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }

        PrimaryButton(if (editing) "Save changes" else "Create case workspace", onClick = {
            when {
                no.isBlank() || title.isBlank() ->
                    error = "Case number and title are required."
                claimant.isBlank() || respondent.isBlank() ->
                    error = "Enter both parties."
                !editing && CaseRepository.exists(no.trim()) ->
                    error = "A case with this number already exists."
                else -> {
                    CaseRepository.upsert(
                        CivilCase(
                            caseNo = no.trim(),
                            title = title.trim(),
                            court = court,
                            caseType = type,
                            nextHearing = hearing.ifBlank { "Not set" },
                            pages = existing?.pages ?: 0,
                            documents = existing?.documents ?: 0,
                            claimant = claimant.trim(),
                            respondent = respondent.trim(),
                            stage = stage
                        )
                    )
                    onDone()
                }
            }
        })
    }

    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { hearing = Dates.formatUtc(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}