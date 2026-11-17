package com.simats.courtease.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.ui.theme.CE

// A rounded bottom sheet that slides up. Use it instead of a plain pop-up dialog.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = CE.Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = CE.Muted, fontSize = 15.sp, lineHeight = 22.sp)
            content()
        }
    }
}

@Composable
private fun uiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color(0xFFF7F9FA),
    focusedBorderColor = CE.Teal,
    unfocusedBorderColor = CE.Border,
    errorBorderColor = CE.Red,
    focusedLabelColor = CE.Teal,
    cursorColor = CE.Teal,
    focusedTextColor = CE.Navy,
    unfocusedTextColor = CE.Navy
)

// A text field with a floating label, an optional character counter, and an inline error.
@Composable
fun UiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    lines: Int = 1,
    maxChars: Int = 0,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val hint: (@Composable () -> Unit)? =
        if (placeholder.isNotEmpty()) ({ Text(placeholder, color = CE.Muted) }) else null
    val support: (@Composable () -> Unit)? =
        if (error != null) ({ Text(error, color = CE.Red) })
        else if (maxChars > 0) ({ Text("${value.length}/$maxChars", color = CE.Muted) })
        else null

    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (maxChars <= 0 || text.length <= maxChars) onValueChange(text) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = hint,
        singleLine = lines <= 1,
        minLines = lines.coerceAtLeast(1),
        maxLines = if (lines <= 1) 1 else lines + 3,
        isError = error != null,
        supportingText = support,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = uiFieldColors()
    )
}

// A friendly empty state: an icon, a headline, a line of help, and an optional button.
@Composable
fun UiEmpty(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CourtCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(CE.Mint),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = CE.Teal, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(
                title, color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(text, color = CE.Muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(actionLabel, onAction)
            }
        }
    }
}

// A green circle with a tick that pops in. Shown after something is saved.
@Composable
fun UiCheck(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(CE.Teal),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(size / 2)) }
}

// A short highlighted passage with a coloured bar on the left. Used for key points and notes.
@Composable
fun UiQuote(label: String, text: String, accent: Color = CE.Teal) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CE.Mint.copy(alpha = 0.55f))
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(12.dp)) {
            Text(label, color = CE.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Text(text, color = CE.Navy, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
