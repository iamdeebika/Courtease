package com.simats.courtease.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
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
import com.simats.courtease.data.AccountRepo
import com.simats.courtease.model.COURTS
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.AuthField
import com.simats.courtease.ui.components.AuthScaffold
import com.simats.courtease.ui.components.DropdownField
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
    var regNo by remember { mutableStateOf("") }
    var council by remember { mutableStateOf("") }
    var court by remember { mutableStateOf(COURTS.first()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        val problem = when {
            name.trim().length < 2 -> "Enter your full name"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email"
            phone.filter { it.isDigit() }.length < 10 -> "Enter a valid phone number"
            regNo.trim().length < 4 ->
                if (role == Role.LAWYER) "Enter your bar enrolment number" else "Enter your employee ID"
            role == Role.LAWYER && council.trim().length < 3 -> "Enter your state bar council"
            password.length < 8 || password.none { it.isDigit() } || password.none { it.isLetter() } ->
                "Password needs 8+ characters with letters and a number"
            password != confirm -> "Passwords don't match"
            else -> null
        }
        if (problem != null) {
            error = problem
            return
        }
        loading = true
        error = null
        scope.launch {
            val result = AccountRepo.signup(
                name, email, phone, password, role, regNo,
                if (role == Role.LAWYER) council else court
            )
            loading = false
            if (result == null) {
                Toast.makeText(context, "Request sent to the court registry", Toast.LENGTH_LONG).show()
                onDone()
            } else {
                error = result
            }
        }
    }

    AuthScaffold(title = "Create account", subtitle = "The registry approves new accounts", onBack = onBack) {
        Text("I am a", color = CE.Navy, fontSize = 16.sp)
        RoleSelector(role) { role = it; error = null }
        AuthField(name, { name = it; error = null }, "Full name", Icons.Default.Person)
        AuthField(email, { email = it; error = null }, "Email", Icons.Default.Email, keyboardType = KeyboardType.Email)
        AuthField(phone, { phone = it; error = null }, "Phone", Icons.Default.Phone, keyboardType = KeyboardType.Phone)
        AuthField(
            regNo, { regNo = it; error = null },
            if (role == Role.LAWYER) "Bar enrolment number" else "Employee ID",
            Icons.Default.Info
        )
        if (role == Role.LAWYER) {
            AuthField(council, { council = it; error = null }, "State bar council", Icons.Default.LocationOn)
        } else {
            DropdownField("Court of posting", court, COURTS, { court = it })
        }
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
        GradientButton("Request account", loading) { submit() }
    }
}