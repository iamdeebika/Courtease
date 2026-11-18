package com.simats.courtease.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.ui.theme.CE

// ---------- buttons ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, 0.97f)
    val shape = RoundedCornerShape(30.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        interactionSource = source
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(CE.Teal, CE.TealLight)), shape),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null)
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun OutlineButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, color: Color = CE.Teal
) {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, 0.97f)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, CE.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = color),
        interactionSource = source
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
    cursorColor = CE.Teal,
    focusedLabelColor = CE.Teal,
    unfocusedLabelColor = CE.Muted
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = CE.Muted) }
        } else null,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = CE.Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = CE.Teal) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Clear", tint = CE.Muted) } }
        } else null,
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
private fun PickerField(
    label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(icon, null, tint = CE.Teal) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick))
    }
}

@Composable
fun DropdownField(
    label: String, value: String, options: List<String>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, value, Icons.Default.KeyboardArrowDown, { open = true }, modifier)
    if (open) {
        UiSheet(title = label, onDismiss = { open = false }) {
            options.forEach { o ->
                val picked = o == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (picked) CE.Mint else Color.Transparent)
                        .clickable { onSelect(o); open = false }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o, color = if (picked) CE.Teal else CE.Navy, fontSize = 17.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (picked) Icon(Icons.Default.Check, null, tint = CE.Teal)
                }
            }
        }
    }
}

@Composable
fun DateField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PickerField(label, value, Icons.Default.DateRange, onClick, modifier)
}
