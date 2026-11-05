package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class KindCountDto(val kind: String, val count: Int)

data class SummaryDto(
    val scope: String,
    @SerializedName("docs_analysed") val docsAnalysed: Int,
    @SerializedName("docs_unread") val docsUnread: Int,
    @SerializedName("ocr_pages") val ocrPages: Int,
    val events: Int,
    val issues: Int,
    @SerializedName("issues_disputed") val issuesDisputed: Int,
    val flags: Int,
    @SerializedName("flags_by_kind") val flagsByKind: List<KindCountDto>,
    @SerializedName("have_plaint") val havePlaint: Boolean,
    @SerializedName("have_written_statement") val haveWrittenStatement: Boolean,
    @SerializedName("ever_reviewed") val everReviewed: Boolean,
    @SerializedName("last_reviewed") val lastReviewed: Long,
    val stale: Boolean,
    val changes: List<String>
)