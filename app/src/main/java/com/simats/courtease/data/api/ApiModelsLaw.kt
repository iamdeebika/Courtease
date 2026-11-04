package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class LawResultDto(
    val ref: String,
    val title: String,
    val court: String,
    val kind: String,
    val date: String,
    val snippet: String,
    val page: Int,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("page_url") val pageUrl: String
)

data class LawSearchDto(
    val status: String,
    val message: String,
    val results: List<LawResultDto>,
    @SerializedName("next_offset") val nextOffset: Int,
    @SerializedName("retry_after") val retryAfter: Int,
    @SerializedName("fallback_url") val fallbackUrl: String
)

data class PageItemDto(val page: Int, val text: String)

data class PagesDto(
    val status: String,
    val message: String,
    val pages: List<PageItemDto>,
    @SerializedName("next_after") val nextAfter: Int,
    @SerializedName("retry_after") val retryAfter: Int
)

data class SavedBody(
    @SerializedName("case_id") val caseId: Int,
    val ref: String,
    val title: String,
    val court: String,
    val kind: String,
    val date: String,
    val snippet: String,
    val page: Int,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("page_url") val pageUrl: String,
    val note: String
)

data class SavedDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    val ref: String,
    val title: String,
    val court: String,
    val kind: String,
    val date: String,
    val snippet: String,
    val page: Int,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("page_url") val pageUrl: String,
    val note: String,
    @SerializedName("created_at") val createdAt: Long
)