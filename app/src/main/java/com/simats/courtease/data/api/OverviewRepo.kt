package com.simats.courtease.data

import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.HistoryDto
import com.simats.courtease.data.api.OverviewClient
import com.simats.courtease.data.api.UpcomingDto
import com.simats.courtease.data.api.safeCall

// Upcoming deadlines and case history. Everything comes from the CourtEase server.
object OverviewRepo {
    private val api get() = OverviewClient.api

    suspend fun upcoming(): ApiResult<UpcomingDto> = safeCall { api.upcoming() }

    suspend fun history(caseId: Int): ApiResult<HistoryDto> = safeCall { api.history(caseId) }
}