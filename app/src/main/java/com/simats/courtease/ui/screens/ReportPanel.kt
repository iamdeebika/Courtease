package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.ReadyReport
import com.simats.courtease.data.ReportOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun ReportSection(caseId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val judge = Session.user?.role == Role.JUDGE
    val title = if (judge) "bench brief" else "case report"
    var ready by remember { mutableStateOf<ReadyReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    InfoBanner(
        if (judge) {
            "The bench brief is a PDF built only from the documents filed in court: case details, the filed record, orders, a timeline, possible issues and points to verify, each with its source page."
        } else {
            "The case report is a PDF of your analysis: case details, documents, orders, a timeline, possible issues, points to verify and your saved precedents, each with its source page."
        }
    )
    if (!judge) {
        WarnBanner(
            "Drafts and privacy",
            "If your side has unfiled drafts, the report includes them and says so on the first page. Keep it private to your side."
        )
    }
    BodyText("It is created fresh each time, from the analysis as it stands now. Text in scripts other than English may print as ? in the PDF.")

    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

    PrimaryButton(if (busy) "Creating..." else "Create $title", {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                when (val r = ReportOps.create(context, caseId)) {
                    is ApiResult.Ok -> ready = r.data
                    is ApiResult.Err -> error = r.message
                }
                busy = false
            }
        }
    })

    val r = ready
    if (r != null) {
        CourtCard {
            Text("Your $title is ready", color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BodyText("${r.file.name} · ${r.sizeKb} KB", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Open", {
                    ReportOps.view(context, r.file)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }, Modifier.weight(1f))
                PrimaryButton("Share", {
                    ReportOps.share(context, r.file, "CourtEase $title")
                        ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }, Modifier.weight(1f))
            }
            BodyText(
                "Sharing sends the file to the app you choose. Share it only with people who are allowed to see it.",
                Modifier.padding(top = 10.dp)
            )
        }
    }
}