package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class CaseSaveBody(
    val title: String,
    @SerializedName("case_type") val caseType: String,
    val court: String,
    val claimant: String,
    val respondent: String,
    @SerializedName("fee_amount") val feeAmount: String,
    @SerializedName("fee_receipt") val feeReceipt: String
)

data class CaseDto(
    val id: Int,
    @SerializedName("case_no") val caseNo: String,
    val title: String,
    @SerializedName("case_type") val caseType: String,
    val court: String,
    val claimant: String,
    val respondent: String,
    val status: String,
    @SerializedName("claimant_team") val claimantTeam: List<Int>,
    @SerializedName("respondent_team") val respondentTeam: List<Int>,
    @SerializedName("fee_amount") val feeAmount: String,
    @SerializedName("fee_receipt") val feeReceipt: String,
    @SerializedName("judge_id") val judgeId: Int,
    val courtroom: String,
    @SerializedName("case_code") val caseCode: String,
    @SerializedName("response_due") val responseDue: String,
    val defects: List<String>,
    @SerializedName("registry_note") val registryNote: String,
    @SerializedName("filed_at") val filedAt: Long
)

data class PreviewDto(
    val id: Int,
    @SerializedName("case_no") val caseNo: String,
    val title: String,
    @SerializedName("case_type") val caseType: String,
    val court: String,
    val claimant: String,
    val respondent: String,
    @SerializedName("response_due") val responseDue: String
)

data class DocBody(
    val name: String,
    @SerializedName("doc_type") val docType: String,
    val pages: Int
)

data class DocDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    val name: String,
    @SerializedName("doc_type") val docType: String,
    val side: String,
    val filed: Boolean,
    @SerializedName("uploaded_by") val uploadedBy: Int,
    val pages: Int,
    @SerializedName("has_file") val hasFile: Boolean,
    val size: Int,
    @SerializedName("created_at") val createdAt: Long
)

data class JoinBody(
    val code: String,
    @SerializedName("file_name") val fileName: String,
    val pages: Int
)

data class ReturnBody(val note: String)

data class AllocateBody(
    @SerializedName("judge_id") val judgeId: Int,
    val reason: String
)

data class OrderBody(val kind: String, val title: String, val body: String)

data class OrderDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    @SerializedName("judge_id") val judgeId: Int,
    val kind: String,
    val title: String,
    val body: String,
    @SerializedName("created_at") val createdAt: Long
)

data class NoteBody(val text: String)

data class NoteDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    @SerializedName("user_id") val userId: Int,
    val text: String,
    @SerializedName("created_at") val createdAt: Long
)

data class SyncDto(
    val cases: List<CaseDto>,
    val docs: List<DocDto>,
    val orders: List<OrderDto>,
    val notes: List<NoteDto>
)