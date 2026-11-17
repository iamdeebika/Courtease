package com.simats.courtease.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
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
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.PushOptionDto
import com.simats.courtease.data.api.PushPrefsRepo
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) BodyText(detail, Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// Lets each user choose which alerts also arrive as push notifications.
@Composable
fun PushSettingsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    var options by remember { mutableStateOf<List<PushOptionDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = PushPrefsRepo.load()) {
            is ApiResult.Ok -> {
                enabled = r.data.enabled
                options = r.data.options
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push notifications") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BodyText("Choose which alerts also reach your phone as notifications. Alerts always stay inside the app. A notification never shows case details.")
                error?.let { Text(it, color = CE.Red, fontSize = 14.sp) }
                if (!loaded && error == null) BodyText("Loading...")
                if (loaded && error == null) {
                    SwitchRow("Push notifications", "Turn all push notifications on or off", enabled) { enabled = it }
                    if (enabled) {
                        options.forEach { option ->
                            SwitchRow(option.label, option.detail, option.enabled) { on ->
                                options = options.map { if (it.key == option.key) it.copy(enabled = on) else it }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (saving || !loaded || error != null) return@TextButton
                saving = true
                scope.launch {
                    val muted = options.filter { !it.enabled }.map { it.key }
                    when (val r = PushPrefsRepo.save(enabled, muted)) {
                        is ApiResult.Ok -> onDismiss()
                        is ApiResult.Err -> error = r.message
                    }
                    saving = false
                }
            }) { Text(if (saving) "Saving..." else "Save", color = CE.Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
