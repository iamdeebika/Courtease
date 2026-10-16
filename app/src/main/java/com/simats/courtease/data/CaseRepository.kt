package com.simats.courtease.data

import com.simats.courtease.model.CivilCase
import com.simats.courtease.model.sampleCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// In-memory store for Sprint 2A. Sprint 2B replaces the inside with Room.
object CaseRepository {
    private val _cases = MutableStateFlow(sampleCases)
    val cases: StateFlow<List<CivilCase>> = _cases

    fun get(caseNo: String): CivilCase? = _cases.value.firstOrNull { it.caseNo == caseNo }

    fun exists(caseNo: String): Boolean = get(caseNo) != null

    fun add(case: CivilCase) {
        _cases.value = _cases.value + case
    }

    fun delete(caseNo: String) {
        _cases.value = _cases.value.filterNot { it.caseNo == caseNo }
    }
}