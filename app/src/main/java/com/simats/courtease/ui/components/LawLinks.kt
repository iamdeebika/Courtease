package com.simats.courtease.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable

// Rows shown on the lawyer's case screen and the judge's bench view.
@Composable
fun LawLinks(onSearch: () -> Unit, onSaved: () -> Unit, onAnalysis: () -> Unit = {}) {
    ToolRow(Icons.Default.Info, "Case analysis", "Read documents, timeline, issues", onAnalysis)
    ToolRow(Icons.Default.Search, "Law and precedent", "Search judgments and orders", onSearch)
    ToolRow(Icons.Default.Star, "Saved precedents", "Only you can see these", onSaved)
}

// Opens a web address in the phone's browser. Only http and https links are allowed.
fun openLink(context: Context, url: String) {
    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
        Toast.makeText(context, "No link available", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found on this phone", Toast.LENGTH_SHORT).show()
    }
}