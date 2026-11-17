package com.simats.courtease.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.ServerConfig
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private suspend fun probe(url: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url + "health").build()).execute().use { r ->
            if (r.isSuccessful) "Reachable: the server answered." else "The server answered with code ${r.code}."
        }
    } catch (e: Exception) {
        "Can't reach $url"
    }
}

// A small line under the sign-in form: shows the server address and lets you change and test it.
@Composable
fun ServerAddressButton() {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text(
        "Server: ${ServerConfig.url().removePrefix("http://").removePrefix("https://").trimEnd('/')}  ·  change",
        color = CE.Muted, fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable {
                text = ServerConfig.url().removeSuffix("/")
                note = ""
                open = true
            }
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Server address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BodyText("Use 127.0.0.1:8000 with the USB cable, or the PC's address on the same Wi-Fi or hotspot, for example 192.168.43.25:8000.")
                    CourtField("Address", text, { text = it; note = "" })
                    if (note.isNotEmpty()) {
                        Text(
                            note, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (note.startsWith("Reachable") || note.startsWith("Saved")) CE.Teal else CE.Red
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val problem = ServerConfig.save(text)
                    note = problem ?: "Saved. Close the app completely and open it again to use the new address."
                }) { Text("Save", color = CE.Teal) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (busy) return@TextButton
                        val url = ServerConfig.normalise(text)
                        if (url == null) {
                            note = "Enter an address like 192.168.43.25:8000"
                        } else {
                            busy = true
                            note = "Testing..."
                            scope.launch {
                                note = probe(url)
                                busy = false
                            }
                        }
                    }) { Text(if (busy) "Testing..." else "Test") }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
            }
        )
    }
}