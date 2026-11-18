package com.simats.courtease.data

import android.content.Context
import android.content.SharedPreferences

// The single base URL of the CourtEase server.
// To point the app at a different server, change BASE_URL below and rebuild. That is the only place.
// An address typed on the login screen ("Server: ... change") overrides it until BASE_URL is changed again.
object ServerConfig {
    // Must end with "/". Examples:
    //   "https://jump-purchase-scoured.ngrok-free.dev/"   ngrok tunnel: works on any network, no cable
    //   "http://192.168.43.25:8000/"                      PC address on the same Wi-Fi or hotspot
    //   "http://127.0.0.1:8000/"                          USB cable only, after "adb reverse tcp:8000 tcp:8000"
    const val BASE_URL = "https://jump-purchase-scoured.ngrok-free.dev/"

    private const val KEY_URL = "url"
    private const val KEY_BUILT_WITH = "built_with"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("courtease_server", Context.MODE_PRIVATE)
        // When BASE_URL changes in a new build, forget any address typed earlier.
        // Before this, an old saved 127.0.0.1 kept winning, so the app only worked with the USB cable.
        if (p.getString(KEY_BUILT_WITH, null) != BASE_URL) {
            p.edit().remove(KEY_URL).putString(KEY_BUILT_WITH, BASE_URL).apply()
        }
        prefs = p
    }

    fun url(): String = prefs?.getString(KEY_URL, null) ?: BASE_URL

    // Turns what was typed into a full address ending in "/".
    //   "192.168.43.25"                       -> "http://192.168.43.25:8000/"
    //   "192.168.43.25:9000"                  -> "http://192.168.43.25:9000/"
    //   "abc.ngrok-free.dev"                  -> "https://abc.ngrok-free.dev/"
    //   "https://abc.ngrok-free.dev"          -> "https://abc.ngrok-free.dev/"
    // Empty input means BASE_URL. Returns null when the text is not a usable address.
    fun normalise(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return BASE_URL
        val lower = text.lowercase()
        val typedScheme = when {
            lower.startsWith("https://") -> "https"
            lower.startsWith("http://") -> "http"
            else -> null
        }
        val rest = (if (typedScheme != null) text.substringAfter("://") else text).trimEnd('/')
        if (!Regex("^[A-Za-z0-9.-]+(:\\d{2,5})?$").matches(rest)) return null
        val host = rest.substringBefore(':')
        val isLocal = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) ||
                host == "localhost" || host.endsWith(".local")
        return if (isLocal) {
            // The PC server has no HTTPS certificate and listens on 8000 unless told otherwise.
            val hostPort = if (rest.contains(':')) rest else "$host:8000"
            "http://$hostPort/"
        } else {
            // A domain name (ngrok, a hosted server): HTTPS on the normal port unless typed otherwise.
            "${typedScheme ?: "https"}://$rest/"
        }
    }

    // Returns an error text, or null when saved.
    fun save(input: String): String? {
        val value = normalise(input) ?: return "Enter an address like 192.168.43.25:8000 or abc.ngrok-free.dev"
        val p = prefs ?: return "Not ready yet. Try again."
        if (value == BASE_URL) p.edit().remove(KEY_URL).apply() else p.edit().putString(KEY_URL, value).apply()
        return null
    }

    // Goes back to BASE_URL.
    fun reset() {
        prefs?.edit()?.remove(KEY_URL)?.apply()
    }
}
