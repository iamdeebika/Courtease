package com.simats.courtease.data

import com.simats.courtease.data.api.AnalysisClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.DocPagesDto
import com.simats.courtease.data.api.ReadingDto
import com.simats.courtease.data.api.TimelineDto
import com.simats.courtease.data.api.safeCall

// Document reading and analysis results. Everything runs on the CourtEase server.
object AnalysisRepo {
    private val api get() = AnalysisClient.api

    suspend fun reading(caseId: Int): ApiResult<ReadingDto> = safeCall { api.reading(caseId) }

    suspend fun start(caseId: Int, force: Boolean): ApiResult<ReadingDto> =
        safeCall { api.startReading(caseId, force) }

    suspend fun pages(docId: Int): ApiResult<DocPagesDto> = safeCall { api.pages(docId) }

    suspend fun timeline(caseId: Int): ApiResult<TimelineDto> = safeCall { api.timeline(caseId) }
}