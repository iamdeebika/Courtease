package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class ReadItemDto(
    @SerializedName("doc_id") val docId: Int,
    val name: String,
    @SerializedName("doc_type") val docType: String,
    val side: String,
    val filed: Boolean,
    val status: String,
    @SerializedName("pages_total") val pagesTotal: Int,
    @SerializedName("pages_text") val pagesText: Int,
    @SerializedName("pages_ocr") val pagesOcr: Int,
    @SerializedName("pages_blank") val pagesBlank: Int,
    @SerializedName("pages_none") val pagesNone: Int,
    val message: String
)

data class ReadingDto(
    val items: List<ReadItemDto>,
    @SerializedName("ocr_ready") val ocrReady: Boolean,
    val busy: Boolean
)

data class PageDto(val page: Int, val method: String, val text: String)

data class DocPagesDto(
    @SerializedName("doc_id") val docId: Int,
    val name: String,
    val pages: List<PageDto>
)