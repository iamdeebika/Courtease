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
import androidx.compose.runtime.LaunchedEffect
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
import com.simats.courtease.data.LawRepo
import com.simats.courtease.data.Store
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.SavedDto
import com.simats.courtease.model.Dates
import com.simats.courtease.model.label
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.openLink
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun SavedPrecedentsScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SavedDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    suspend fun load() {
        when (val r = LawRepo.saved(caseId)) {
            is ApiResult.Ok -> {
                items = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
        loaded = true
    }

    LaunchedEffect(caseId) { load() }

    val caseLabel: String = Store.caseById(caseId)?.label ?: "Case"

    CourtScaffold(onBack = onBack) {
        ScreenHeader("RESEARCH", "Saved precedents", caseLabel)
        InfoBanner("Only you can see what you save here. Add a precedent you found on Indian Kanoon or another source by typing its details.")
        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

        PrimaryButton("Add a precedent I found", { adding = true })
        OutlineButton(
            "Open the Indian Kanoon website",
            { openLink(context, "https://indiankanoon.org") },
            Modifier.fillMaxWidth()
        )

        if (loaded && items.isEmpty() && error == null) {
            EmptyCard("Nothing saved", "Save results from Law and precedent, or add one you found yourself.")
        }

        items.forEach { p ->
            val manual = p.kind == "manual"
            CourtCard {
                Text(p.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val parts = if (manual) listOf(p.court, p.date) else listOf(p.court, p.date, p.kind)
                val meta = parts.filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) BodyText(meta, Modifier.padding(top = 4.dp))
                if (manual && p.ref.isNotBlank()) {
                    Text(
                        "Citation: ${p.ref}", color = CE.Teal, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (p.snippet.isNotBlank()) {
                    BodyText((if (manual) "Key point: " else "") + p.snippet, Modifier.padding(top = 8.dp))
                }
                if (!manual && p.page > 0) {
                    Text(
                        "Page ${p.page}", color = CE.Teal, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (p.note.isNotBlank()) {
                    Text("Note: ${p.note}", color = CE.Navy, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                }
                BodyText(
                    "Saved ${Dates.stamp(p.createdAt)} · " + (if (manual) "Added by you" else "Source: kanoon.dev"),
                    Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.sourceUrl.isNotBlank()) {
                        OutlineButton("Open link", { openLink(context, p.sourceUrl) }, Modifier.weight(1f))
                    }
                    OutlineButton("Remove", {
                        scope.launch {
                            when (val r = LawRepo.unsave(p.id)) {
                                is ApiResult.Ok -> {
                                    Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                                    load()
                                }
                                is ApiResult.Err -> Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, Modifier.weight(1f), color = CE.Red)
                }
            }
        }
    }

    if (adding) {
        ManualPrecedentDialog(
            caseId = caseId,
            onDone = {
                adding = false
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                scope.launch { load() }
            },
            onDismiss = { adding = false }
        )
    }
}