package com.simats.courtease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.model.Role
import com.simats.courtease.ui.theme.CE

val AuthNavyDeep = Color(0xFF0B1F33)
val AuthGold = Color(0xFFF2B84B)
val AuthTealLight = Color(0xFF3E8E8A)

@Composable
fun AuthScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthNavyDeep, CE.Navy, CE.Teal)))
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(2.dp, AuthGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("⚖", color = Color.White, fontSize = 30.sp) }
                Spacer(Modifier.height(8.dp))
                Text("CourtEase", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = CE.Navy, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = CE.Muted, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(icon, null, tint = CE.Teal) },
        trailingIcon = trailing,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CE.Teal,
            unfocusedBorderColor = CE.Border,
            focusedLabelColor = CE.Teal,
            cursorColor = CE.Teal,
            focusedContainerColor = Color(0xFFF7F9FA),
            unfocusedContainerColor = Color(0xFFF7F9FA),
            focusedTextColor = CE.Navy,
            unfocusedTextColor = CE.Navy
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ShowHideButton(hidden: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(if (hidden) "Show" else "Hide", color = CE.Teal, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GradientButton(text: String, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(CE.Teal, AuthTealLight)))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoleSelector(selected: Role, onSelect: (Role) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoleTile("⚖", "Judge", selected == Role.JUDGE, CE.Navy, Modifier.weight(1f)) { onSelect(Role.JUDGE) }
        RoleTile("💼", "Lawyer", selected == Role.LAWYER, AuthGold, Modifier.weight(1f)) { onSelect(Role.LAWYER) }
    }
}

@Composable
private fun RoleTile(
    emoji: String,
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) CE.Mint else CE.Chip)
            .border(2.dp, if (selected) CE.Teal else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 22.sp, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (selected) CE.Teal else CE.Navy,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ErrorLine(message: String?) {
    if (message != null) Text(message, color = CE.Red, fontSize = 15.sp)
}