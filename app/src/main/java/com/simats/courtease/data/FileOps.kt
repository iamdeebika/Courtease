package com.simats.courtease.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.safeCall
import com.simats.courtease.model.Doc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class PickedDoc(val uri: Uri, val name: String, val pages: Int)

// Uploads files to the server and opens downloaded ones.
object FileOps {
    private const val MAX_BYTES = 15 * 1024 * 1024
    private val api get() = ApiClient.api

    private fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaType())

    private suspend fun readBytes(context: Context, uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
        }

    private fun filePart(context: Context, p: PickedDoc, bytes: ByteArray): MultipartBody.Part {
        val mime = context.contentResolver.getType(p.uri) ?: "application/octet-stream"
        return MultipartBody.Part.createFormData("file", p.name, bytes.toRequestBody(mime.toMediaType()))
    }

    suspend fun uploadDoc(context: Context, caseId: Int, type: String, p: PickedDoc): String? {
        val bytes = readBytes(context, p.uri) ?: return "Couldn't read this file"
        if (bytes.size > MAX_BYTES) return "The file is larger than 15 MB"
        val part = filePart(context, p, bytes)
        return when (val r = safeCall { api.upload(caseId, text(type), text(p.pages.toString()), part) }) {
            is ApiResult.Ok -> {
                AccountRepo.syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }
    }

    suspend fun joinWithFile(context: Context, code: String, p: PickedDoc): String? {
        val bytes = readBytes(context, p.uri) ?: return "Couldn't read this file"
        if (bytes.size > MAX_BYTES) return "The file is larger than 15 MB"
        val part = filePart(context, p, bytes)
        return when (val r = safeCall { api.joinFile(text(code.trim()), text(p.pages.toString()), part) }) {
            is ApiResult.Ok -> {
                AccountRepo.syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }
    }

    // Downloads a document and opens it in a viewer app. Returns an error text, or null on success.
    suspend fun open(context: Context, d: Doc): String? {
        if (!d.hasFile) return "This is a sample document with no file attached"
        return when (val r = safeCall { api.content(d.id) }) {
            is ApiResult.Err -> r.message
            is ApiResult.Ok -> {
                val body = r.data
                val mime = body.contentType()?.toString()?.substringBefore(";") ?: "application/pdf"
                val ext = when {
                    mime.contains("pdf") -> ".pdf"
                    mime.contains("png") -> ".png"
                    else -> ".jpg"
                }
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "docs")
                    dir.mkdirs()
                    val f = File(dir, "doc_${d.id}$ext")
                    f.writeBytes(body.bytes())
                    f
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    null
                } catch (e: ActivityNotFoundException) {
                    "No app on this phone can open this file"
                }
            }
        }
    }
}