package com.simats.courtease.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.PapersRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.IntegrityCheckDto
import com.simats.courtease.model.Dates
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

private fun originText(origin: String): String = when (origin) {
    "upload" -> "recorded when the file was uploaded"
    "backfill" -> "recorded later, when fingerprinting was added to the system, so a change made before then cannot be detected"
    else -> "not recorded"
}

// One line under a document: "Integrity verified" with a short code. Filed documents are checked automatically.
@Composable
fun IntegrityBadge(docId: Int, filed: Boolean, hasFile: Boolean) {
    if (!hasFile) return
    val scope = rememberCoroutineScope()
    var result by remember(docId) { mutableStateOf<IntegrityCheckDto?>(null) }
    var error by remember(docId) { mutableStateOf<String?>(null) }
    var busy by remember(docId) { mutableStateOf(false) }
    var showing by remember(docId) { mutableStateOf(false) }

    fun run() {
        if (busy) return
        busy = true
        scope.launch {
            when (val r = PapersRepo.check(docId)) {
                is ApiResult.Ok -> {
                    result = r.data
                    error = null
                }
                is ApiResult.Err -> error = r.message
            }
            busy = false
        }
    }

    LaunchedEffect(docId) { if (filed) run() }

    val r = result
    val line: String
    val color: Color
    when {
        busy && r == null -> { line = "Checking integrity..."; color = CE.Muted }
        r == null && error != null -> { line = "Integrity check unavailable. Tap to retry."; color = CE.AmberText }
        r == null -> { line = "Tap to check integrity"; color = CE.Muted }
        r.status == "verified" -> { line = "✓ Integrity verified · ${r.code}"; color = CE.Teal }
        r.status == "mismatch" -> { line = "✗ File does not match its recorded fingerprint"; color = CE.Red }
        r.status == "missing" -> { line = "✗ Stored file is missing on the server"; color = CE.Red }
        r.status == "unrecorded" -> { line = "No fingerprint recorded yet"; color = CE.AmberText }
        else -> { line = "No file to check"; color = CE.Muted }
    }

    Text(
        line, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable {
                if (r == null) run()
                showing = true
            }
    )

    if (showing && r != null) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Document integrity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.message, color = CE.Navy, fontSize = 15.sp)
                    if (r.sha256.isNotBlank()) {
                        BodyText("Fingerprint (SHA-256), ${originText(r.origin)}:")
                        Text(r.sha256, color = CE.Navy, fontSize = 12.sp)
                        BodyText("Recorded ${Dates.stamp(r.recordedAt)} · ${r.size / 1024} KB")
                    }
                    BodyText("The fingerprint detects a changed or missing file. It does not prove who wrote the document.")
                }
            },
            confirmButton = { TextButton(onClick = { run() }) { Text(if (busy) "Checking..." else "Check again") } },
            dismissButton = { TextButton(onClick = { showing = false }) { Text("Close") } }
        )
    }
}