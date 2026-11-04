package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class PassageFlagDto(
    @SerializedName("doc_id") val docId: Int,
    @SerializedName("doc_name") val docName: String,
    @SerializedName("doc_type") val docType: String,
    val side: String,
    val filed: Boolean,
    val page: Int,
    val method: String,
    val role: String,
    val sentence: String
)

data class FlagDto(
    val key: String,
    val kind: String,
    val topic: String,
    val title: String,
    val summary: String,
    @SerializedName("claimant_values") val claimantValues: List<String>,
    @SerializedName("respondent_values") val respondentValues: List<String>,
    val claimant: List<PassageFlagDto>,
    val respondent: List<PassageFlagDto>
)

data class FlagsDto(
    val flags: List<FlagDto>,
    @SerializedName("docs_used") val docsUsed: Int,
    val unread: List<UnreadDocDto>,
    @SerializedName("have_plaint") val havePlaint: Boolean,
    @SerializedName("have_written_statement") val haveWrittenStatement: Boolean,
    @SerializedName("have_evidence") val haveEvidence: Boolean
)