package com.simats.courtease.data

import android.content.Context
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.IntegrityCheckDto
import com.simats.courtease.data.api.PapersClient
import com.simats.courtease.data.api.PapersDto
import com.simats.courtease.data.api.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

// Integrity checks, notice and order PDFs. Everything goes through the CourtEase server.
object PapersRepo {
    private val api get() = PapersClient.api

    suspend fun check(docId: Int): ApiResult<IntegrityCheckDto> = safeCall { api.integrity(docId) }

    suspend fun papers(caseId: Int): ApiResult<PapersDto> = safeCall { api.papers(caseId) }

    suspend fun noticeBytes(caseId: Int): ApiResult<ByteArray> = bytes { api.noticePdf(caseId) }

    suspend fun orderBytes(orderId: Int): ApiResult<ByteArray> = bytes { api.orderPdf(orderId) }

    private suspend fun bytes(call: suspend () -> Response<ResponseBody>): ApiResult<ByteArray> =
        when (val r = safeCall(call)) {
            is ApiResult.Ok -> try {
                ApiResult.Ok(withContext(Dispatchers.IO) { r.data.bytes() })
            } catch (e: Exception) {
                ApiResult.Err("Couldn't read the file")
            }
            is ApiResult.Err -> ApiResult.Err(r.message, r.code)
        }

    // Saves a PDF in the app's private cache so a viewer can open it or the share sheet can send it.
    suspend fun save(context: Context, name: String, data: ByteArray): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "docs")
            dir.mkdirs()
            val file = File(dir, name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
            file.writeBytes(data)
            file
        }
}