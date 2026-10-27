package com.simats.courtease.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.AuthRepository
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.AuthField
import com.simats.courtease.ui.components.AuthScaffold
import com.simats.courtease.ui.components.ErrorLine
import com.simats.courtease.ui.components.GradientButton
import com.simats.courtease.ui.components.RoleSelector
import com.simats.courtease.ui.components.ShowHideButton
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.launch

@Composable
fun SignupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.LAWYER) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun check(): String? = when {
        name.trim().length < 2 -> "Enter your full name"
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email"
        !phone.replace(" ", "").replace("-", "").matches(Regex("^\\+?\\d{10,15}$")) -> "Enter a valid phone number"
        password.length < 8 || password.none { it.isDigit() } || password.none { it.isLetter() } ->
            "Password needs 8+ characters with letters and a number"
        password != confirm -> "Passwords don't match"
        else -> null
    }

    fun submit() {
        val problem = check()
        if (problem != null) {
            error = problem
            return
        }
        loading = true
        error = null
        scope.launch {
            val result = AuthRepository.signup(
                name.trim(), email.trim(), phone.trim(), role.name.lowercase(), password
            )
            when (result) {
                is ApiResult.Ok -> {
                    loading = false
                    Toast.makeText(context, "Account created. Sign in.", Toast.LENGTH_LONG).show()
                    onDone()
                }
                is ApiResult.Err -> {
                    error = result.message
                    loading = false
                }
            }
        }
    }

    AuthScaffold(title = "Create account", onBack = onBack) {
        AuthField(name, { name = it; error = null }, "Full name", Icons.Default.Person)
        AuthField(email, { email = it; error = null }, "Email", Icons.Default.Email, keyboardType = KeyboardType.Email)
        AuthField(phone, { phone = it; error = null }, "Phone", Icons.Default.Phone, keyboardType = KeyboardType.Phone)
        Text("I am a", color = CE.Navy, fontSize = 16.sp)
        RoleSelector(role) { role = it }
        AuthField(
            password, { password = it; error = null }, "Password", Icons.Default.Lock,
            keyboardType = KeyboardType.Password, masked = hidden,
            trailing = { ShowHideButton(hidden) { hidden = !hidden } }
        )
        AuthField(
            confirm, { confirm = it; error = null }, "Confirm password", Icons.Default.Lock,
            keyboardType = KeyboardType.Password, masked = hidden, imeAction = ImeAction.Done
        )
        ErrorLine(error)
        GradientButton("Create account", loading) { submit() }
    }
}