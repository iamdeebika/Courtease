package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class ManualBody(
    @SerializedName("case_id") val caseId: Int,
    val title: String,
    val citation: String,
    val court: String,
    val date: String,
    val summary: String,
    @SerializedName("source_url") val sourceUrl: String,
    val note: String
)