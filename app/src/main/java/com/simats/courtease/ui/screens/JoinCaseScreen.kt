package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepo
import com.simats.courtease.data.FileOps
import com.simats.courtease.data.PickedDoc
import com.simats.courtease.data.Store
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.model.CaseFile
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.rememberDocPicker
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun JoinCaseScreen(onBack: () -> Unit, onJoined: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<CaseFile?>(null) }
    var picked by remember { mutableStateOf<PickedDoc?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberDocPicker { file ->
        picked = file
        error = null
    }

    CourtScaffold(onBack = onBack) {
        ScreenHeader("RESPONDENT", "Join a case", "Enter the code from the court notice")

        CourtField(
            "Case code", code,
            { code = it.uppercase(); found = null; error = null },
            placeholder = "CE-XXXXX"
        )

        val f = found
        if (f == null) {
            error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
            PrimaryButton(if (busy) "Searching..." else "Find case", {
                if (!busy) {
                    busy = true
                    scope.launch {
                        when (val result = CaseRepo.preview(code)) {
                            is ApiResult.Ok -> found = result.data
                            is ApiResult.Err -> error = result.message
                        }
                        busy = false
                    }
                }
            })
        } else {
            CourtCard {
                Text(f.caseNo.ifBlank { "Case" }, color = CE.Muted, fontSize = 15.sp)
                Text(f.title, color = CE.Navy, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                BodyText("${f.caseType.substringAfter("· ")} · ${f.court}", Modifier.padding(top = 4.dp))
                BodyText("Claimant: ${f.claimant}")
                BodyText("Respondent: ${f.respondent}")
                BodyText("Response due ${f.responseDue}")
            }

            CourtCard {
                Text("Your vakalatnama", color = CE.Navy, fontSize = 19.sp)
                BodyText("Upload the document that authorises you to appear.", Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    picked?.name ?: "Choose PDF, JPG or PNG",
                    { pick() }, Modifier.fillMaxWidth()
                )
            }

            error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

            PrimaryButton(if (busy) "Submitting..." else "Confirm appearance", {
                val file = picked
                if (file == null) {
                    error = "Upload your vakalatnama"
                } else if (!busy) {
                    busy = true
                    scope.launch {
                        val result = FileOps.joinWithFile(context, code, file)
                        busy = false
                        if (result == null) {
                            Toast.makeText(context, "You now appear for the respondent", Toast.LENGTH_LONG).show()
                            onJoined(f.id)
                        } else {
                            error = result
                        }
                    }
                }
            })
        }
    }
}