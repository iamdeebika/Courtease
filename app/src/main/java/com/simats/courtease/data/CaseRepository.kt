package com.simats.courtease.data

import android.content.Context
import com.simats.courtease.model.CivilCase
import com.simats.courtease.model.sampleCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Room is the saved copy. The StateFlow is what the screens read.
object CaseRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dao: CaseDao? = null

    private val _cases = MutableStateFlow<List<CivilCase>>(emptyList())
    val cases: StateFlow<List<CivilCase>> = _cases

    fun init(context: Context) {
        if (dao != null) return
        val app = context.applicationContext
        val d = AppDatabase.create(app).caseDao()
        dao = d
        val prefs = app.getSharedPreferences("courtease", Context.MODE_PRIVATE)
        scope.launch {
            if (!prefs.getBoolean("seeded_v2", false)) {
                sampleCases.forEach { d.insert(it.toEntity()) }
                prefs.edit().putBoolean("seeded_v2", true).apply()
            }
            _cases.value = d.getAll().map { it.toModel() }
        }
    }

    fun get(caseNo: String): CivilCase? = _cases.value.firstOrNull { it.caseNo == caseNo }

    fun exists(caseNo: String): Boolean = get(caseNo) != null

    fun upsert(case: CivilCase) {
        val list = _cases.value
        _cases.value =
            if (list.any { it.caseNo == case.caseNo }) list.map { if (it.caseNo == case.caseNo) case else it }
            else list + case
        scope.launch { dao?.insert(case.toEntity()) }
    }

    fun delete(caseNo: String) {
        _cases.value = _cases.value.filterNot { it.caseNo == caseNo }
        scope.launch { dao?.delete(caseNo) }
    }
}