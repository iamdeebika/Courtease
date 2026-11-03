package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class StatusCountDto(val status: String, val count: Int)

data class JudgeLoadDto(
    val name: String,
    val court: String,
    val courtroom: String,
    @SerializedName("open_cases") val openCases: Int,
    @SerializedName("closed_cases") val closedCases: Int
)

data class ReportDto(
    @SerializedName("pending_accounts") val pendingAccounts: Int,
    @SerializedName("approved_judges") val approvedJudges: Int,
    @SerializedName("approved_lawyers") val approvedLawyers: Int,
    @SerializedName("suspended_accounts") val suspendedAccounts: Int,
    @SerializedName("total_cases") val totalCases: Int,
    @SerializedName("filed_last_7_days") val filedLast7Days: Int,
    @SerializedName("awaiting_scrutiny") val awaitingScrutiny: Int,
    @SerializedName("awaiting_allocation") val awaitingAllocation: Int,
    @SerializedName("awaiting_notice") val awaitingNotice: Int,
    @SerializedName("overdue_responses") val overdueResponses: Int,
    @SerializedName("documents_filed") val documentsFiled: Int,
    @SerializedName("orders_issued") val ordersIssued: Int,
    @SerializedName("by_status") val byStatus: List<StatusCountDto>,
    val judges: List<JudgeLoadDto>
)