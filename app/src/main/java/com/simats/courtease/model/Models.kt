package com.simats.courtease.model

enum class Role { JUDGE, LAWYER }

data class CivilCase(
    val caseNo: String,
    val title: String,
    val court: String,
    val caseType: String,
    val nextHearing: String,
    val pages: Int,
    val documents: Int
)

val sampleCases = listOf(
    CivilCase(
        "CE-2026-0147", "Ravi vs Kumar Traders", "Civil Court, Chennai",
        "Contract Dispute", "24 Oct 2026", 512, 38
    ),
    CivilCase(
        "CE-2026-0152", "Meena vs Sundaram", "District Court, Madurai",
        "Property Dispute", "02 Nov 2026", 240, 17
    ),
    CivilCase(
        "CE-2026-0160", "ABC Pvt Ltd vs XYZ Ltd", "Civil Court, Coimbatore",
        "Recovery Suit", "15 Nov 2026", 120, 9
    )
)