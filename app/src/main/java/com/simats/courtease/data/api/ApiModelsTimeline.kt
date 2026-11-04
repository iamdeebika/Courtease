package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class TimelineEventDto(
    @SerializedName("date_iso") val dateIso: String,
    @SerializedName("date_text") val dateText: String,
    val label: String,
    val sentence: String,
    @SerializedName("doc_id") val docId: Int,
    @SerializedName("doc_name") val docName: String,
    @SerializedName("doc_type") val docType: String,
    val side: String,
    val filed: Boolean,
    val page: Int,
    val method: String,
    val amounts: List<String>
)

data class UnreadDocDto(
    @SerializedName("doc_id") val docId: Int,
    val name: String,
    val reason: String
)

data class TimelineDto(
    val events: List<TimelineEventDto>,
    @SerializedName("docs_used") val docsUsed: Int,
    val unread: List<UnreadDocDto>
)