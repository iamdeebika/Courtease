package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class UpcomingItemDto(
    val kind: String,
    val title: String,
    val detail: String,
    @SerializedName("case_id") val caseId: Int,
    @SerializedName("case_label") val caseLabel: String,
    @SerializedName("date_iso") val dateIso: String,
    @SerializedName("date_text") val dateText: String,
    val days: Int,
    @SerializedName("due_text") val dueText: String
)

data class UpcomingDto(
    val items: List<UpcomingItemDto>,
    val overdue: Int,
    val total: Int
)

data class HistoryEventDto(
    val kind: String,
    val text: String,
    val actor: String,
    val role: String,
    val at: Long,
    val recorded: Boolean
)

data class HistoryDto(
    val events: List<HistoryEventDto>,
    val incomplete: Boolean
)