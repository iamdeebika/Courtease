package com.simats.courtease.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Store
import com.simats.courtease.model.User
import com.simats.courtease.ui.components.AuthField
import com.simats.courtease.ui.components.AuthScaffold
import com.simats.courtease.ui.components.ErrorLine
import com.simats.courtease.ui.components.GradientButton
import com.simats.courtease.ui.components.ShowHideButton
import com.simats.courtease.ui.theme.CE

@Composable
fun LoginScreen(onLoggedIn: (User) -> Unit, onSignup: () -> Unit, onForgot: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (email.isBlank() || password.isEmpty()) {
            error = "Enter your email and password"
            return
        }
        val user = Store.login(email, password)
        if (user == null) error = "Wrong email or password" else onLoggedIn(user)
    }

    AuthScaffold(title = "Welcome back", subtitle = "Sign in to continue") {
        AuthField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )
        AuthField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            icon = Icons.Default.Lock,
            keyboardType = KeyboardType.Password,
            masked = hidden,
            imeAction = ImeAction.Done,
            trailing = { ShowHideButton(hidden) { hidden = !hidden } }
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Forgot password?",
                color = CE.Teal,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onForgot)
            )
        }
        ErrorLine(error)
        GradientButton("Sign in") { submit() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("New here? ", color = CE.Muted, fontSize = 16.sp)
            Text(
                "Create account",
                color = CE.Teal,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSignup)
            )
        }
    }
}