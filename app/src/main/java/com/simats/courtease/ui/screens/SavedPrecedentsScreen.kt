package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
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
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.components.UiEmpty
import com.simats.courtease.ui.components.UiQuote
import com.simats.courtease.ui.components.UiSheet
import com.simats.courtease.ui.components.openLink
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
private fun PrecedentCard(p: SavedDto, onOpenLink: () -> Unit, onRemove: () -> Unit) {
    val manual = p.kind == "manual"
    CourtCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (manual) CE.Amber else CE.Mint),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Star, null, tint = if (manual) CE.AmberText else CE.Teal) }
            Column(Modifier.weight(1f)) {
                Text(p.title, color = CE.Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val meta = listOf(p.court, p.date).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) BodyText(meta, Modifier.padding(top = 2.dp))
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (manual) Pill("Added by you", CE.Amber, CE.AmberText) else Pill("kanoon.dev")
            if (!manual && p.page > 0) Pill("Page ${p.page}", CE.Chip, CE.Navy)
        }
        if (manual && p.ref.isNotBlank()) {
            Text(
                "Citation: ${p.ref}", color = CE.Teal, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (p.snippet.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            UiQuote(if (manual) "KEY POINT" else "EXTRACT", p.snippet)
        }
        if (p.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            UiQuote("YOUR NOTE", p.note, CE.Gold)
        }
        BodyText("Saved ${Dates.stamp(p.createdAt)}", Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.sourceUrl.isNotBlank()) {
                OutlineButton("Open link", onOpenLink, Modifier.weight(1f))
            }
            OutlineButton("Remove", onRemove, Modifier.weight(1f), color = CE.Red)
        }
    }
}

@Composable
fun SavedPrecedentsScreen(caseId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SavedDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SavedDto?>(null) }

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
        InfoBanner("Only you can see what you save here. Add a judgment you found on Indian Kanoon or elsewhere by typing its details.")
        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

        if (items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard("%02d".format(items.size), "Saved", Modifier.weight(1f))
                StatCard("%02d".format(items.count { it.kind == "manual" }), "Added by you", Modifier.weight(1f))
            }
        }

        PrimaryButton("Add a precedent", { adding = true }, icon = Icons.Default.Add)
        OutlineButton(
            "Search Indian Kanoon website",
            { openLink(context, "https://indiankanoon.org") },
            Modifier.fillMaxWidth(),
            icon = Icons.Default.Search
        )

        if (loaded && items.isEmpty() && error == null) {
            UiEmpty(
                Icons.Default.Star, "No precedents yet",
                "Save a result from Law and precedent, or add a judgment you found yourself.",
                "Add a precedent"
            ) { adding = true }
        }

        items.forEach { p ->
            PrecedentCard(p, { openLink(context, p.sourceUrl) }, { removing = p })
        }
    }

    if (adding) {
        ManualPrecedentDialog(
            caseId = caseId,
            onDone = {
                adding = false
                scope.launch { load() }
            },
            onDismiss = { adding = false }
        )
    }

    removing?.let { target ->
        UiSheet("Remove this precedent?", { removing = null }, subtitle = target.title) {
            BodyText("It will be removed from your saved list and from your next report.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton("Keep", { removing = null }, Modifier.weight(1f))
                PrimaryButton("Remove", {
                    removing = null
                    scope.launch {
                        when (val r = LawRepo.unsave(target.id)) {
                            is ApiResult.Ok -> {
                                Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                                load()
                            }
                            is ApiResult.Err -> Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}
