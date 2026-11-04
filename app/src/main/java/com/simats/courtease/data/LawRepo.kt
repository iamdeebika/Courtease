package com.simats.courtease.data

import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.LawClient
import com.simats.courtease.data.api.LawResultDto
import com.simats.courtease.data.api.LawSearchDto
import com.simats.courtease.data.api.MessageDto
import com.simats.courtease.data.api.PagesDto
import com.simats.courtease.data.api.SavedBody
import com.simats.courtease.data.api.SavedDto
import com.simats.courtease.data.api.safeCall

// Law search and saved precedents. Everything goes through the CourtEase server.
object LawRepo {
    private val api get() = LawClient.api

    suspend fun search(query: String, offset: Int): ApiResult<LawSearchDto> =
        safeCall { api.search(query.trim(), offset) }

    // One page of extracted text. The page number is 1-based; the server wants "after the previous page".
    suspend fun pageText(ref: String, page: Int): ApiResult<PagesDto> =
        safeCall { api.pages(ref, (page - 1).coerceAtLeast(0), 1) }

    suspend fun save(caseId: Int, r: LawResultDto, note: String): String? =
        when (
            val x = safeCall {
                api.save(
                    SavedBody(
                        caseId, r.ref, r.title, r.court, r.kind, r.date,
                        r.snippet, r.page, r.sourceUrl, r.pageUrl, note.trim()
                    )
                )
            }
        ) {
            is ApiResult.Ok -> null
            is ApiResult.Err -> x.message
        }

    suspend fun saved(caseId: Int): ApiResult<List<SavedDto>> = safeCall { api.saved(caseId) }

    suspend fun unsave(id: Int): ApiResult<MessageDto> = safeCall { api.unsave(id) }
}