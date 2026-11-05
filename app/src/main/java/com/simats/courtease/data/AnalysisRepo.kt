package com.simats.courtease.data

import com.simats.courtease.data.api.AnalysisClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.DocPagesDto
import com.simats.courtease.data.api.FlagsDto
import com.simats.courtease.data.api.IssuesDto
import com.simats.courtease.data.api.ReadingDto
import com.simats.courtease.data.api.SummaryDto
import com.simats.courtease.data.api.TimelineDto
import com.simats.courtease.data.api.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Document reading and analysis results. Everything runs on the CourtEase server.
object AnalysisRepo {
    private val api get() = AnalysisClient.api

    suspend fun reading(caseId: Int): ApiResult<ReadingDto> = safeCall { api.reading(caseId) }

    suspend fun start(caseId: Int, force: Boolean): ApiResult<ReadingDto> =
        safeCall { api.startReading(caseId, force) }

    suspend fun pages(docId: Int): ApiResult<DocPagesDto> = safeCall { api.pages(docId) }

    suspend fun timeline(caseId: Int): ApiResult<TimelineDto> = safeCall { api.timeline(caseId) }

    suspend fun issues(caseId: Int): ApiResult<IssuesDto> = safeCall { api.issues(caseId) }

    suspend fun flags(caseId: Int): ApiResult<FlagsDto> = safeCall { api.flags(caseId) }

    suspend fun summary(caseId: Int): ApiResult<SummaryDto> = safeCall { api.summary(caseId) }

    // Returns an error text, or null when the review was saved.
    suspend fun markSeen(caseId: Int): String? =
        when (val r = safeCall { api.markSeen(caseId) }) {
            is ApiResult.Ok -> null
            is ApiResult.Err -> r.message
        }

    // Downloads the PDF report as bytes.
    suspend fun reportBytes(caseId: Int): ApiResult<ByteArray> =
        when (val r = safeCall { api.report(caseId) }) {
            is ApiResult.Ok -> try {
                ApiResult.Ok(withContext(Dispatchers.IO) { r.data.bytes() })
            } catch (e: Exception) {
                ApiResult.Err("Couldn't read the report")
            }
            is ApiResult.Err -> ApiResult.Err(r.message, r.code)
        }
}