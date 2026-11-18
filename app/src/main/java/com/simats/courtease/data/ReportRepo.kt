package com.simats.courtease.data

import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.MessageDto
import com.simats.courtease.data.api.ReportDto
import com.simats.courtease.data.api.safeCall

// Registry report numbers and the manual reminder run.
object ReportRepo {
    suspend fun report(): ApiResult<ReportDto> = safeCall { ApiClient.api.report() }

    suspend fun runReminders(): ApiResult<MessageDto> = safeCall { ApiClient.api.runReminders() }
}
