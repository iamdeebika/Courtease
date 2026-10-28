package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.Session
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

@Composable
fun JoinCaseScreen(onBack: () -> Unit, onJoined: (Int) -> Unit) {
    val context = LocalContext.current
    val me = Session.userId
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<CaseFile?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pages by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberDocPicker { name, count ->
        fileName = name
        pages = count
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
            PrimaryButton("Find case", {
                val result = CaseOps.previewByCode(code)
                if (result == null) error = "No case found for this code" else found = result
            })
        } else {
            CourtCard {
                Text("CASE DETAILS", color = CE.Muted, fontSize = 15.sp)
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
                    if (fileName.isBlank()) "Choose PDF or image" else fileName,
                    { pick() }, Modifier.fillMaxWidth()
                )
            }

            error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

            PrimaryButton("Confirm appearance", {
                val result = CaseOps.joinAsRespondent(me, code, fileName, pages)
                if (result == null) {
                    Toast.makeText(context, "You now appear for the respondent", Toast.LENGTH_LONG).show()
                    onJoined(f.id)
                } else {
                    error = result
                }
            })
        }
    }
}