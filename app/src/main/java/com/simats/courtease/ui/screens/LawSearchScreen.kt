package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.LawRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.LawResultDto
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.SearchField
import com.simats.courtease.ui.components.WarnBanner
import com.simats.courtease.ui.components.openLink
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun LawSearchScreen(caseId: Int, onBack: () -> Unit, onOpenSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<LawResultDto>() }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var nextOffset by remember { mutableIntStateOf(-1) }
    var fallback by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf<LawResultDto?>(null) }
    var saving by remember { mutableStateOf<LawResultDto?>(null) }

    fun run(offset: Int) {
        if (loading) return
        if (query.trim().length < 2) {
            error = "Type at least 2 letters"
            return
        }
        loading = true
        error = null
        scope.launch {
            when (val r = LawRepo.search(query, offset)) {
                is ApiResult.Ok -> {
                    val d = r.data
                    if (offset == 0) results.clear()
                    results.addAll(d.results)
                    status = d.status
                    message = d.message
                    nextOffset = d.nextOffset
                    fallback = d.fallbackUrl
                    searched = true
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    CourtScaffold(
        onBack = onBack,
        actions = { BarIcon(Icons.Default.Star, "Saved", onOpenSaved) }
    ) {
        ScreenHeader("RESEARCH", "Law and precedent")
        InfoBanner("Search by legal topic, like \"specific performance\" or \"bail\". Don't type party names, case numbers or case facts.")

        SearchField(query, { query = it; error = null }, "Legal topic")
        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }
        PrimaryButton(if (loading) "Searching..." else "Search", { run(0) })

        if (searched && status != "ok") {
            WarnBanner(
                if (status == "busy") "Law database busy" else "Law database unavailable",
                message
            )
            if (fallback.isNotBlank()) {
                OutlineButton("Search on the Indian Kanoon website", { openLink(context, fallback) }, Modifier.fillMaxWidth())
            }
        }

        if (searched && status == "ok" && results.isEmpty()) {
            EmptyCard("No results", message.ifBlank { "Try other words." })
            if (fallback.isNotBlank()) {
                OutlineButton("Try on the Indian Kanoon website", { openLink(context, fallback) }, Modifier.fillMaxWidth())
            }
        }

        results.forEach { r ->
            CourtCard {
                Text(r.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val meta = listOf(r.court, r.date, r.kind).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) BodyText(meta, Modifier.padding(top = 4.dp))
                if (r.snippet.isNotBlank()) BodyText(r.snippet, Modifier.padding(top = 8.dp))
                if (r.page > 0) {
                    Text(
                        "Matching page ${r.page}", color = CE.Teal, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.ref.isNotBlank() && r.page > 0) {
                        OutlineButton("Read", { reading = r }, Modifier.weight(1f))
                    }
                    if (r.sourceUrl.isNotBlank()) {
                        OutlineButton("Source", { openLink(context, r.sourceUrl) }, Modifier.weight(1f))
                    }
                    PrimaryButton("Save", { saving = r }, Modifier.weight(1f))
                }
                BodyText("Source: kanoon.dev", Modifier.padding(top = 8.dp))
            }
        }

        if (nextOffset >= 0) {
            OutlineButton(if (loading) "Loading..." else "More results", { run(nextOffset) }, Modifier.fillMaxWidth())
        }

        BodyText("Pilot sample only. Not finding something does not mean no law or judgment exists. Always check the original source.")
    }

    reading?.let { r -> PageDialog(r) { reading = null } }

    saving?.let { r ->
        SaveDialog(
            caseId = caseId,
            r = r,
            onDone = { text ->
                saving = null
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { saving = null }
        )
    }
}

@Composable
private fun PageDialog(r: LawResultDto, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Loading...") }

    LaunchedEffect(r.ref, r.page) {
        when (val x = LawRepo.pageText(r.ref, r.page)) {
            is ApiResult.Ok -> {
                val d = x.data
                if (d.status != "ok") note = d.message
                else if (d.pages.isEmpty()) note = "No text came back for this page."
                else {
                    text = d.pages.first().text
                    note = ""
                }
            }
            is ApiResult.Err -> note = x.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page ${r.page}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note.isNotEmpty()) BodyText(note) else Text(text, color = CE.Navy, fontSize = 15.sp)
                BodyText("Extracted text can contain mistakes. Check the original source before relying on it.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SaveDialog(caseId: Int, r: LawResultDto, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to this case") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(r.title, color = CE.Navy, fontSize = 16.sp)
                BodyText("Only you can see saved precedents.")
                CourtField("Note (optional)", note, { note = it; error = null })
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (busy) return@TextButton
                busy = true
                scope.launch {
                    val problem = LawRepo.save(caseId, r, note)
                    busy = false
                    if (problem == null) onDone("Saved") else error = problem
                }
            }) { Text(if (busy) "Saving..." else "Save", color = CE.Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}