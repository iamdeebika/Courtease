package com.simats.courtease.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette taken from the Figma designs.
object CE {
    val Navy = Color(0xFF132E45)
    val Teal = Color(0xFF2F6B6B)
    val Mint = Color(0xFFE6F0EE)
    val Bg = Color(0xFFF4F6F8)
    val Border = Color(0xFFD9E0E6)
    val Track = Color(0xFFDDE3E8)
    val Muted = Color(0xFF5F6B76)
    val Chip = Color(0xFFEDF0F3)
    val Amber = Color(0xFFFFF1D6)
    val AmberText = Color(0xFF8A5A00)
    val Red = Color(0xFFB3261E)
}

private val Scheme = lightColorScheme(
    primary = CE.Teal,
    onPrimary = Color.White,
    secondary = CE.Navy,
    background = CE.Bg,
    surface = Color.White,
    onBackground = CE.Navy,
    onSurface = CE.Navy
)

// Light theme only, so the screens match the Figma designs on every phone.
@Composable
fun CourtEaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}