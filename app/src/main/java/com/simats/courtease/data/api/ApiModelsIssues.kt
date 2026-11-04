package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class PassageDto(
    @SerializedName("doc_id") val docId: Int,
    @SerializedName("doc_name") val docName: String,
    @SerializedName("doc_type") val docType: String,
    val side: String,
    val filed: Boolean,
    val page: Int,
    val method: String,
    val role: String,
    val sentence: String,
    val figures: List<String>,
    val denial: Boolean
)

data class IssueDto(
    val key: String,
    val title: String,
    val plain: String,
    val status: String,
    @SerializedName("status_text") val statusText: String,
    val found: Int,
    val passages: List<PassageDto>
)

data class IssuesDto(
    val issues: List<IssueDto>,
    @SerializedName("docs_used") val docsUsed: Int,
    val unread: List<UnreadDocDto>,
    @SerializedName("have_plaint") val havePlaint: Boolean,
    @SerializedName("have_written_statement") val haveWrittenStatement: Boolean
)