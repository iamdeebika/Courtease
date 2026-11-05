package com.simats.courtease.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.simats.courtease.R

// Arimo: a free font that looks almost identical to Helvetica.
val Arimo = FontFamily(
    Font(R.font.arimo_regular, FontWeight.Normal),
    Font(R.font.arimo_medium, FontWeight.Medium),
    Font(R.font.arimo_semibold, FontWeight.SemiBold),
    Font(R.font.arimo_bold, FontWeight.Bold)
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Arimo),
    displayMedium = base.displayMedium.copy(fontFamily = Arimo),
    displaySmall = base.displaySmall.copy(fontFamily = Arimo),
    headlineLarge = base.headlineLarge.copy(fontFamily = Arimo),
    headlineMedium = base.headlineMedium.copy(fontFamily = Arimo),
    headlineSmall = base.headlineSmall.copy(fontFamily = Arimo),
    titleLarge = base.titleLarge.copy(fontFamily = Arimo),
    titleMedium = base.titleMedium.copy(fontFamily = Arimo),
    titleSmall = base.titleSmall.copy(fontFamily = Arimo),
    bodyLarge = base.bodyLarge.copy(fontFamily = Arimo),
    bodyMedium = base.bodyMedium.copy(fontFamily = Arimo),
    bodySmall = base.bodySmall.copy(fontFamily = Arimo),
    labelLarge = base.labelLarge.copy(fontFamily = Arimo),
    labelMedium = base.labelMedium.copy(fontFamily = Arimo),
    labelSmall = base.labelSmall.copy(fontFamily = Arimo)
)