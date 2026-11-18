package com.simats.courtease.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.ReportRepo
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.ReportDto
import kotlinx.coroutines.launch

// State and actions for the registry Reports screen.
class ReportViewModel : ViewModel() {
    var report by mutableStateOf<ReportDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when (val r = ReportRepo.report()) {
            is ApiResult.Ok -> {
                report = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
    }

    // Sends reminders, then refreshes. onMessage receives the text to show in a toast.
    fun sendReminders(onMessage: (String) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val message = when (val x = ReportRepo.runReminders()) {
                is ApiResult.Ok -> x.data.message
                is ApiResult.Err -> x.message
            }
            onMessage(message)
            AccountRepo.syncAll()
            load()
            busy = false
        }
    }
}
