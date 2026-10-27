package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.Session
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.BarIcon
import com.simats.courtease.ui.components.BodyText
import com.simats.courtease.ui.components.ChoiceChip
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.InfoBanner
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.ScreenHeader
import com.simats.courtease.ui.components.VSpace
import com.simats.courtease.ui.theme.CE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var role by remember { mutableStateOf(Role.LAWYER) }
    var error by remember { mutableStateOf<String?>(null) }

    CourtScaffold(
        actions = { BarIcon(Icons.Default.Lock, "Secure") {} }
    ) {
        ScreenHeader(
            "01 / SECURE ACCESS",
            "Your case. In focus.",
            "Secure legal workflows. Source-backed AI support. Professional judgment, always."
        )

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Mint).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚖", color = CE.Teal, fontSize = 34.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Welcome to CourtEase", color = CE.Navy, fontSize = 20.sp)
                Text("For authorized legal professionals and parties", color = CE.Teal, fontSize = 15.sp)
            }
        }

        Text("Sign in securely", color = CE.Navy, fontSize = 24.sp, modifier = Modifier.padding(top = 4.dp))

        CourtField(
            label = "Email or professional ID",
            value = email,
            onValueChange = { email = it; error = null },
            keyboardType = KeyboardType.Email,
            trailing = { Icon(Icons.Default.Person, null, tint = CE.Teal) }
        )
        CourtField(
            label = "Password",
            value = password,
            onValueChange = { password = it; error = null },
            masked = hidden,
            keyboardType = KeyboardType.Password,
            trailing = {
                TextButton(onClick = { hidden = !hidden }) {
                    Text(if (hidden) "Show" else "Hide", color = CE.Teal)
                }
            }
        )
        Text(
            "Forgot password?", color = CE.Teal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                Toast.makeText(context, "Password reset is available once the backend is connected.", Toast.LENGTH_SHORT).show()
            }
        )

        CourtCard {
            Text("Access follows your assigned role", color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            VSpace(12)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Role.entries.forEach { r -> ChoiceChip(r.label, role == r) { role = r } }
            }
            VSpace(12)
            BodyText("Your institution assigns permissions. Signing in does not grant or change your role. (Demo: choose a role to preview its workspace.)")
        }

        InfoBanner("Multi-factor authentication follows sign-in. Confirm the code from your authenticator to continue.")

        error?.let { Text(it, color = CE.Red, fontSize = 15.sp) }

        PrimaryButton("Secure sign-in", icon = Icons.Default.Lock, onClick = {
            if (email.isBlank() || password.isBlank()) {
                error = "Enter your email or professional ID and password."
            } else {
                Session.signIn(email.trim(), role)
                onSignedIn()
            }
        })
    }
}