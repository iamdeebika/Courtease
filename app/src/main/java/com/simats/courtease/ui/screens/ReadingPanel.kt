package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.AnalysisRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.PageDto
import com.simats.courtease.data.api.ReadItemDto
import com.simats.courtease.data.api.ReadingDto
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun statusLine(i: ReadItemDto): String = when (i.status) {
    "done" -> buildString {
        append("${i.pagesTotal} page(s): ${i.pagesText} typed, ${i.pagesOcr} read by OCR")
        if (i.pagesNone > 0) append(", ${i.pagesNone} need OCR")
        if (i.pagesBlank > 0) append(", ${i.pagesBlank} blank")
    }
    "reading" -> "Reading..."
    "failed" -> "Could not read. ${i.message}"
    "nofile" -> "Sample entry with no file attached"
    else -> "Not read yet"
}

private fun methodLabel(method: String): String = when (method) {
    "text" -> "typed text"
    "ocr" -> "read by OCR"
    "blank" -> "blank page"
    else -> "needs OCR"
}

@Composable
fun ReadingSection(caseId: Int) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ReadingDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<ReadItemDto?>(null) }

    suspend fun load() {
        when (val r = AnalysisRepo.reading(caseId)) {
            is ApiResult.Ok -> {
                data = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
    }

    fun start(force: Boolean) {
        if (starting) return
        starting = true
        scope.launch {
            when (val r = AnalysisRepo.start(caseId, force)) {
                is ApiResult.Ok -> {
                    data = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
            starting = false
        }
    }

    LaunchedEffect(caseId) {
        while (true) {
            load()
            delay(if (data?.busy == true) 2000 else 8000)
        }
    }

    error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

    val d = data
    if (d != null && !d.ocrReady) {
        WarnBanner("OCR is not installed", "Typed PDFs can be read. Scanned pages need Tesseract on the server.")
    }

    PrimaryButton(if (starting || d?.busy == true) "Reading..." else "Read new documents", { start(false) })
    OutlineButton("Read all again", { start(true) }, Modifier.fillMaxWidth())

    if (d == null) {
        if (error == null) EmptyCard("Loading", "Checking the documents.")
    } else if (d.items.isEmpty()) {
        EmptyCard("No documents", "Upload documents to this case first.")
    }

    d?.items?.forEach { i ->
        CourtCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(i.name, color = CE.Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (i.filed) Pill("Filed") else Pill("Draft", CE.Chip, CE.Muted)
            }
            BodyText("${i.docType} · ${i.side}", Modifier.padding(top = 4.dp))
            Text(
                statusLine(i), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (i.status == "failed") CE.Red else CE.Teal,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (i.status == "done" && i.message.isNotBlank()) BodyText(i.message, Modifier.padding(top = 4.dp))
            if (i.status == "done" && (i.pagesText + i.pagesOcr) > 0) {
                Spacer(Modifier.height(10.dp))
                OutlineButton("View text", { viewing = i }, Modifier.fillMaxWidth())
            }
        }
    }

    viewing?.let { item -> TextDialog(item) { viewing = null } }
}

@Composable
private fun TextDialog(item: ReadItemDto, onDismiss: () -> Unit) {
    var pages by remember { mutableStateOf<List<PageDto>>(emptyList()) }
    var note by remember { mutableStateOf("Loading...") }

    LaunchedEffect(item.docId) {
        when (val r = AnalysisRepo.pages(item.docId)) {
            is ApiResult.Ok -> {
                pages = r.data.pages
                note = if (pages.isEmpty()) "No text was found." else ""
            }
            is ApiResult.Err -> note = r.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (note.isNotEmpty()) BodyText(note)
                pages.forEach { p ->
                    Text(
                        "Page ${p.page} · ${methodLabel(p.method)}", color = CE.Teal,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(p.text.ifBlank { "(no text)" }, color = CE.Navy, fontSize = 14.sp)
                }
                BodyText("OCR text can contain mistakes. Check the original page.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}