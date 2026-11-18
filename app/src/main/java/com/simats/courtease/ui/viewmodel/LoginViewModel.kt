package com.simats.courtease.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.data.Session
import com.simats.courtease.model.User
import kotlinx.coroutines.launch

// State and actions for the sign-in screen. The screen only draws this state and forwards taps.
class LoginViewModel(app: Application) : AndroidViewModel(app) {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var hidden by mutableStateOf(true)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onEmail(value: String) { email = value; error = null }
    fun onPassword(value: String) { password = value; error = null }
    fun toggleHidden() { hidden = !hidden }

    fun submit(onLoggedIn: (User) -> Unit) {
        if (loading) return
        if (email.isBlank() || password.isEmpty()) {
            error = "Enter your email and password"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            val problem = AccountRepo.login(getApplication(), email, password)
            loading = false
            if (problem != null) error = problem else Session.user?.let(onLoggedIn)
        }
    }
}
