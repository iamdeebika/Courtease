package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.PapersRepo
import com.simats.courtease.data.ReportOps
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.PapersDto
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun PapersSection(caseId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<PapersDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            when (val r = PapersRepo.papers(caseId)) {
                is ApiResult.Ok -> {
                    data = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
        }
    }

    LaunchedEffect(caseId) { load() }

    fun openPaper(key: String, share: Boolean, fileName: String, fetch: suspend () -> ApiResult<ByteArray>) {
        if (busy.isNotEmpty()) return
        busy = key
        scope.launch {
            when (val r = fetch()) {
                is ApiResult.Ok -> {
                    val file = PapersRepo.save(context, fileName, r.data)
                    val problem = if (share) ReportOps.share(context, file, fileName) else ReportOps.view(context, file)
                    problem?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
                is ApiResult.Err -> Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
            }
            busy = ""
        }
    }

    InfoBanner("The notice and the orders download as PDFs. Each order shows a fingerprint code. Share a paper only with people who are allowed to see it.")
    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

    val d = data
    if (d == null) {
        if (error == null) EmptyCard("Loading", "Fetching the papers.")
        return
    }

    SectionTitle("Notice")
    if (!d.noticeAvailable) {
        EmptyCard("No notice yet", d.noticeNote)
    } else {
        CourtCard {
            Text("Notice to the respondent", color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BodyText(d.noticeNote, Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(if (busy == "n-open") "Preparing..." else "Open PDF", {
                    openPaper("n-open", false, "CourtEase_Notice_$caseId.pdf") { PapersRepo.noticeBytes(caseId) }
                }, Modifier.weight(1f))
                PrimaryButton(if (busy == "n-share") "Preparing..." else "Share", {
                    openPaper("n-share", true, "CourtEase_Notice_$caseId.pdf") { PapersRepo.noticeBytes(caseId) }
                }, Modifier.weight(1f))
            }
        }
    }

    SectionTitle("Orders (${d.orders.size})")
    if (d.orders.isEmpty()) {
        EmptyCard("No orders yet", "Orders issued by the judge appear here as soon as they are issued.")
    }
    d.orders.forEach { o ->
        CourtCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(o.title, color = CE.Navy, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Pill(o.kind)
            }
            BodyText("${o.judge} · ${o.date}", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(if (busy == "o-open-${o.id}") "Preparing..." else "Open PDF", {
                    openPaper("o-open-${o.id}", false, "CourtEase_Order_${o.id}.pdf") { PapersRepo.orderBytes(o.id) }
                }, Modifier.weight(1f))
                PrimaryButton(if (busy == "o-share-${o.id}") "Preparing..." else "Share", {
                    openPaper("o-share-${o.id}", true, "CourtEase_Order_${o.id}.pdf") { PapersRepo.orderBytes(o.id) }
                }, Modifier.weight(1f))
            }
        }
    }
}