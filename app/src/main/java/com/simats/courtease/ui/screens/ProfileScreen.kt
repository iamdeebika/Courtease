package com.simats.courtease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import com.simats.courtease.data.CaseOps
import com.simats.courtease.data.CommonOps
import com.simats.courtease.data.JudgeOps
import com.simats.courtease.data.Session
import com.simats.courtease.data.Store
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Role
import com.simats.courtease.ui.components.AuthGold
import com.simats.courtease.ui.components.CourtCard
import com.simats.courtease.ui.components.CourtField
import com.simats.courtease.ui.components.CourtScaffold
import com.simats.courtease.ui.components.EmptyCard
import com.simats.courtease.ui.components.OutlineButton
import com.simats.courtease.ui.components.Pill
import com.simats.courtease.ui.components.PrimaryButton
import com.simats.courtease.ui.components.SectionTitle
import com.simats.courtease.ui.components.StatCard
import com.simats.courtease.ui.theme.CE

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = CE.Muted, fontSize = 15.sp, modifier = Modifier.weight(0.4f))
        Text(value.ifBlank { "-" }, color = CE.Navy, fontSize = 15.sp, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val u = Session.user
    val me = Session.userId

    var name by remember(u?.id) { mutableStateOf(u?.name ?: "") }
    var phone by remember(u?.id) { mutableStateOf(u?.phone ?: "") }
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var profileMsg by remember { mutableStateOf<String?>(null) }
    var profileOk by remember { mutableStateOf(true) }
    var passMsg by remember { mutableStateOf<String?>(null) }
    var passOk by remember { mutableStateOf(true) }

    CourtScaffold(onBack = onBack) {
        if (u == null) {
            EmptyCard("Not signed in", "Sign in to see your profile.")
            return@CourtScaffold
        }
        val accent = when (u.role) {
            Role.JUDGE -> AuthGold
            Role.LAWYER -> CE.Teal
            Role.REGISTRY -> CE.Navy
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    u.name.trim().removePrefix("Justice ").take(1).uppercase().ifEmpty { "?" },
                    color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(u.name, color = CE.Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(u.email, color = CE.Muted, fontSize = 15.sp)
            Pill(u.role.designation)
        }

        // ---------- numbers for this role ----------
        when (u.role) {
            Role.LAWYER -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard("%02d".format(CaseOps.casesOf(me).size), "My cases", Modifier.weight(1f))
                StatCard(
                    "%02d".format(Store.state.tasks.count { it.assigneeId == me && !it.done }),
                    "Open tasks", Modifier.weight(1f)
                )
            }
            Role.JUDGE -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard(
                    "%02d".format(JudgeOps.casesOf(me).count { it.status != CaseStatus.CLOSED }),
                    "Open cases", Modifier.weight(1f)
                )
                StatCard(
                    "%02d".format(Store.state.orders.count { it.judgeId == me }),
                    "Orders issued", Modifier.weight(1f)
                )
            }
            Role.REGISTRY -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard(
                    "%02d".format(Store.state.users.count { it.status == AccountStatus.PENDING }),
                    "Account requests", Modifier.weight(1f)
                )
                StatCard("%02d".format(Store.state.audit.size), "Audit entries", Modifier.weight(1f))
            }
        }

        // ---------- registry details (set by the registry, read-only) ----------
        SectionTitle("Registry record")
        CourtCard {
            Detail("Registry ID", u.registryId)
            when (u.role) {
                Role.LAWYER -> {
                    Detail("Bar number", u.regNo)
                    Detail("Bar council", u.council)
                }
                Role.JUDGE -> {
                    Detail("Employee ID", u.regNo)
                    Detail("Court", u.court)
                    Detail("Courtroom", u.courtroom)
                }
                Role.REGISTRY -> Detail("Office", "Court Registry")
            }
            Detail("Status", u.status.name.lowercase().replaceFirstChar { it.uppercase() })
        }

        // ---------- editable details ----------
        SectionTitle("Your details")
        CourtCard {
            CourtField("Full name", name, { name = it; profileMsg = null })
            Spacer(Modifier.height(12.dp))
            CourtField("Email", u.email, {}, enabled = false)
            Spacer(Modifier.height(12.dp))
            CourtField("Phone", phone, { phone = it; profileMsg = null }, keyboardType = KeyboardType.Phone)
            profileMsg?.let {
                Text(
                    it, color = if (profileOk) CE.Teal else CE.Red, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Save changes", {
                val r = CommonOps.updateProfile(me, name, phone)
                profileOk = r == null
                profileMsg = r ?: "Saved"
            })
        }

        // ---------- password ----------
        SectionTitle("Password")
        CourtCard {
            CourtField("Current password", current, { current = it; passMsg = null }, masked = true, keyboardType = KeyboardType.Password)
            Spacer(Modifier.height(12.dp))
            CourtField("New password", newPass, { newPass = it; passMsg = null }, masked = true, keyboardType = KeyboardType.Password)
            Spacer(Modifier.height(12.dp))
            CourtField("Confirm new password", confirm, { confirm = it; passMsg = null }, masked = true, keyboardType = KeyboardType.Password)
            passMsg?.let {
                Text(
                    it, color = if (passOk) CE.Teal else CE.Red, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Update password", {
                val r = if (newPass != confirm) "Passwords don't match"
                else CommonOps.changePassword(me, current, newPass)
                passOk = r == null
                passMsg = r ?: "Password updated"
                if (r == null) {
                    current = ""; newPass = ""; confirm = ""
                    Toast.makeText(context, "Password updated", Toast.LENGTH_SHORT).show()
                }
            })
        }

        OutlineButton("Sign out", onSignOut, Modifier.fillMaxWidth(), color = CE.Red)
    }
}