package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class IntegrityCheckDto(
    @SerializedName("doc_id") val docId: Int,
    val status: String,
    val message: String,
    val code: String,
    val sha256: String,
    val origin: String,
    @SerializedName("recorded_at") val recordedAt: Long,
    val size: Int
)

data class OrderItemDto(
    val id: Int,
    val kind: String,
    val title: String,
    val date: String,
    val judge: String
)

data class PapersDto(
    @SerializedName("notice_available") val noticeAvailable: Boolean,
    @SerializedName("notice_note") val noticeNote: String,
    val orders: List<OrderItemDto>
)