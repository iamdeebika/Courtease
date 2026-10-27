package com.simats.courtease.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.simats.courtease.model.CivilCase

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey val caseNo: String,
    val title: String,
    val court: String,
    val caseType: String,
    val nextHearing: String,
    val pages: Int,
    val documents: Int,
    val claimant: String,
    val respondent: String,
    val stage: String
)

fun CaseEntity.toModel() = CivilCase(
    caseNo, title, court, caseType, nextHearing, pages, documents, claimant, respondent, stage
)

fun CivilCase.toEntity() = CaseEntity(
    caseNo, title, court, caseType, nextHearing, pages, documents, claimant, respondent, stage
)