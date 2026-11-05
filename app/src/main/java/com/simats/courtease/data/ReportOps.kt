package com.simats.courtease.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.simats.courtease.data.api.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ReadyReport(val file: File, val sizeKb: Int)

// Downloads the PDF report, saves it in the app's private cache, and opens or shares it.
object ReportOps {
    suspend fun create(context: Context, caseId: Int): ApiResult<ReadyReport> =
        when (val r = AnalysisRepo.reportBytes(caseId)) {
            is ApiResult.Err -> ApiResult.Err(r.message, r.code)
            is ApiResult.Ok -> try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "docs")
                    dir.mkdirs()
                    val f = File(dir, "report_$caseId.pdf")
                    f.writeBytes(r.data)
                    f
                }
                ApiResult.Ok(ReadyReport(file, (file.length() / 1024).toInt().coerceAtLeast(1)))
            } catch (e: Exception) {
                ApiResult.Err("Couldn't save the report on this phone")
            }
        }

    private fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // Returns an error text, or null when a viewer opened.
    fun view(context: Context, file: File): String? = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "No PDF viewer found. Install one, such as Google Drive."
    } catch (e: Exception) {
        "Couldn't open the report"
    }

    // Returns an error text, or null when the share sheet opened.
    fun share(context: Context, file: File, subject: String): String? = try {
        val uri = uriOf(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        null
    } catch (e: Exception) {
        "Couldn't open the share sheet"
    }
}