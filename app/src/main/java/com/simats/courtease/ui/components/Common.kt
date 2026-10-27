package com.simats.courtease.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.data.CaseRepository
import com.simats.courtease.model.CivilCase
import com.simats.courtease.ui.theme.CE

// ---------- helpers ----------

@Composable
fun rememberCase(caseNo: String): CivilCase? {
    val cases by CaseRepository.cases.collectAsState()
    return cases.firstOrNull { it.caseNo == caseNo }
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

// ---------- scaffold ----------

@Composable
fun CourtTopBar(onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CE.Navy)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        } else {
            Text("⚖", color = Color.White, fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text("CourtEase", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = Color.White)
    }
}

@Composable
fun CourtScaffold(
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    bottomSpace: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = CE.Bg,
        topBar = { CourtTopBar(onBack, actions) },
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets.navigationBars
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = bottomSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) { content() }
    }
}

// ---------- text ----------

@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, color = CE.Teal, fontSize = 13.sp, letterSpacing = 0.5.sp)
        Text(title, color = CE.Navy, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
        if (subtitle != null) Text(subtitle, color = CE.Muted, fontSize = 16.sp, lineHeight = 23.sp)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = CE.Navy, fontSize = 22.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = CE.Teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = CE.Muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = modifier)
}

// ---------- cards and banners ----------

@Composable
fun CourtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.White,
    borderColor: Color = CE.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(color)
        .border(1.dp, borderColor, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(18.dp), content = content)
}

@Composable
fun Pill(text: String, bg: Color = CE.Mint, fg: Color = CE.Teal) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) CE.Mint else CE.Chip)
            .border(1.dp, if (selected) CE.Teal else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text, color = if (selected) CE.Teal else CE.Navy, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun InfoBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Mint).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = CE.Teal)
        Spacer(Modifier.width(12.dp))
        Text(text, color = CE.Teal, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
fun WarnBanner(title: String, text: String, onClick: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Amber)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(m.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = CE.AmberText, fontSize = 20.sp)
        Text(text, color = CE.AmberText, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, fraction: Float? = null) {
    CourtCard(modifier) {
        Text(value, color = if (fraction != null) CE.Teal else CE.Navy, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (fraction != null) CE.Navy else CE.Muted, fontSize = 16.sp,
            fontWeight = if (fraction != null) FontWeight.SemiBold else FontWeight.Normal)
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            ThinProgress(fraction)
        }
    }
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(CE.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(CE.Teal))
    }
}

@Composable
fun ToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(CE.Mint),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = CE.Teal) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = CE.Muted, fontSize = 15.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CE.Teal)
        }
    }
}

@Composable
fun CaseCard(c: CivilCase, onClick: () -> Unit) {
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.caseNo, color = CE.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Pill(c.stage)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, color = CE.Navy, fontSize = 22.sp)
                Text("${c.court} · ${c.documents} documents", color = CE.Muted, fontSize = 15.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CE.Teal)
        }
    }
}

@Composable
fun StageProgress(stage: String, stages: List<String>) {
    val current = stages.indexOf(stage).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stages.forEachIndexed { i, _ ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50))
                    .background(if (i <= current) CE.Teal else CE.Track)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { i, s ->
            Text(
                s, modifier = Modifier.weight(1f), fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (i == current) CE.Teal else CE.Muted,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun EmptyCard(title: String, text: String) {
    CourtCard {
        Text(title, color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        BodyText(text)
    }
}

// ---------- buttons ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CE.Teal, contentColor = Color.White)
    ) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, color: Color = CE.Teal
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CE.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = color)
    ) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- form fields ----------

@Composable
fun FieldLabel(text: String) {
    Text(text, color = CE.Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = CE.Chip,
    focusedBorderColor = CE.Teal,
    unfocusedBorderColor = CE.Border,
    disabledBorderColor = CE.Border,
    focusedTextColor = CE.Navy,
    unfocusedTextColor = CE.Navy,
    disabledTextColor = CE.Muted,
    cursorColor = CE.Teal
)

@Composable
fun CourtField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    masked: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = CE.Muted) }
            } else null,
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailing,
            shape = RoundedCornerShape(20.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = CE.Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = CE.Teal) },
        shape = RoundedCornerShape(30.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PickerBox(value: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CE.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, color = CE.Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = CE.Teal)
    }
}

@Composable
fun DropdownField(
    label: String, value: String, options: List<String>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Box {
            PickerBox(value.ifBlank { "Select" }, Icons.Default.KeyboardArrowDown) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

@Composable
fun DateField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        FieldLabel(label)
        PickerBox(value.ifBlank { "Pick a date" }, Icons.Default.DateRange, onClick)
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun SmallCircle(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}