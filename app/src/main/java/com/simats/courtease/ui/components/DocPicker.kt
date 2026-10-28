package com.simats.courtease.ui.components

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && cursor.moveToFirst()) return cursor.getString(i)
    }
    return uri.lastPathSegment ?: "Document"
}

private fun countPages(context: Context, uri: Uri): Int = try {
    val type = context.contentResolver.getType(uri).orEmpty()
    if (type == "application/pdf") {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        } ?: 1
    } else {
        1
    }
} catch (e: Exception) {
    1
}

// Returns a function that opens the phone's file picker. The callback gets the file name and page count.
@Composable
fun rememberDocPicker(onPicked: (name: String, pages: Int) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onPicked(displayName(context, uri), countPages(context, uri))
    }
    return { launcher.launch(arrayOf("application/pdf", "image/*")) }
}