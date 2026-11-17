package com.simats.courtease.data

import android.content.Context
import android.content.SharedPreferences

// The address of the CourtEase server. It can be changed from the login screen without rebuilding the app.
object ServerConfig {
    private const val DEFAULT = "http://127.0.0.1:8000/"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("courtease_server", Context.MODE_PRIVATE)
    }

    fun url(): String = prefs?.getString("url", null) ?: DEFAULT

    // Turns "192.168.43.25" or "https://192.168.43.25:8000" into "http://192.168.43.25:8000/".
    // The demo server has no HTTPS certificate, so a local address always uses http.
    // Empty input means the default address. Returns null when the text is not a usable address.
    fun normalise(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return DEFAULT
        val hadScheme = text.startsWith("http://") || text.startsWith("https://")
        val rest = (if (hadScheme) text.substringAfter("://") else text).trimEnd('/')
        if (!Regex("^[A-Za-z0-9.-]+(:\\d{2,5})?$").matches(rest)) return null
        val host = rest.substringBefore(':')
        val isLocal = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) ||
                host == "localhost" || host.endsWith(".local")
        val scheme = if (isLocal || !text.startsWith("https://")) "http" else "https"
        val hostPort = if (rest.contains(':')) rest else "$host:8000"
        return "$scheme://$hostPort/"
    }

    // Returns an error text, or null when saved.
    fun save(input: String): String? {
        val value = normalise(input) ?: return "Enter an address like 192.168.43.25 or 192.168.43.25:8000"
        prefs?.edit()?.putString("url", value)?.apply()
        return null
    }
}