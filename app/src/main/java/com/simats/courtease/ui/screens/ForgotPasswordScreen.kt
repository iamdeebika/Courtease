package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.simats.courtease.data.Store
import com.simats.courtease.ui.components.AuthField
import com.simats.courtease.ui.components.AuthScaffold
import com.simats.courtease.ui.components.ErrorLine
import com.simats.courtease.ui.components.GradientButton
import com.simats.courtease.ui.components.ShowHideButton

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when {
            email.isBlank() -> { error = "Enter your email"; return }
            phone.isBlank() -> { error = "Enter your registered phone"; return }
            password.length < 8 || password.none { it.isDigit() } || password.none { it.isLetter() } -> {
                error = "Password needs 8+ characters with letters and a number"; return
            }
            password != confirm -> { error = "Passwords don't match"; return }
        }
        val result = Store.resetPassword(email, phone, password)
        if (result == null) {
            Toast.makeText(context, "Password updated. Sign in.", Toast.LENGTH_LONG).show()
            onDone()
        } else {
            error = result
        }
    }

    AuthScaffold(title = "Reset password", subtitle = "Verify with your phone", onBack = onBack) {
        AuthField(email, { email = it; error = null }, "Email", Icons.Default.Email, keyboardType = KeyboardType.Email)
        AuthField(phone, { phone = it; error = null }, "Registered phone", Icons.Default.Phone, keyboardType = KeyboardType.Phone)
        AuthField(
            password, { password = it; error = null }, "New password", Icons.Default.Lock,
            keyboardType = KeyboardType.Password, masked = hidden,
            trailing = { ShowHideButton(hidden) { hidden = !hidden } }
        )
        AuthField(
            confirm, { confirm = it; error = null }, "Confirm password", Icons.Default.Lock,
            keyboardType = KeyboardType.Password, masked = hidden, imeAction = ImeAction.Done
        )
        ErrorLine(error)
        GradientButton("Reset password") { submit() }
    }
}