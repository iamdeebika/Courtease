package com.simats.courtease.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.courtease.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// State and actions for the "Server address" dialog on the sign-in screen.
class ServerAddressViewModel : ViewModel() {
    var current by mutableStateOf(ServerConfig.url())
        private set
    var open by mutableStateOf(false)
        private set
    var text by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set

    val noteIsGood: Boolean get() = note.startsWith("Reachable") || note.startsWith("Saved")

    fun show() {
        text = ServerConfig.url().removeSuffix("/")
        note = ""
        open = true
    }

    fun close() { open = false }

    fun onText(value: String) { text = value; note = "" }

    fun save() {
        val problem = ServerConfig.save(text)
        current = ServerConfig.url()
        note = problem ?: "Saved. The app now uses ${current.removeSuffix("/")}"
    }

    fun useDefault() {
        ServerConfig.reset()
        current = ServerConfig.url()
        text = current.removeSuffix("/")
        note = "Saved. Back to the built-in address."
    }

    fun test() {
        if (busy) return
        val url = ServerConfig.normalise(text)
        if (url == null) {
            note = "Enter an address like 192.168.43.25:8000 or abc.ngrok-free.dev"
            return
        }
        busy = true
        note = "Testing..."
        viewModelScope.launch {
            note = probe(url)
            busy = false
        }
    }

    private suspend fun probe(url: String): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(url + "health")
                .header("ngrok-skip-browser-warning", "true")
                .build()
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful) "Reachable: the server answered." else "The server answered with code ${r.code}."
            }
        } catch (e: Exception) {
            "Can't reach $url"
        }
    }
}
