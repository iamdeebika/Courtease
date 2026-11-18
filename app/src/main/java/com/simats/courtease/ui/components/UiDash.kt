package com.simats.courtease.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.ui.theme.CE

private fun initialsOf(name: String): String {
    val skip = setOf("justice", "adv", "advocate", "mr", "ms", "mrs", "dr", "hon")
    val parts = name.split(" ", ".").filter { it.isNotBlank() && it.lowercase() !in skip }
    val letters = parts.take(2).map { it.first().uppercaseChar() }.joinToString("")
    return letters.ifBlank { "CE" }
}

// The greeting card at the top of a dashboard: gradient, title, subtitle and an initials avatar.
@Composable
fun DashHero(eyebrow: String, title: String, subtitle: String, initialsFrom: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "hero"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enter; translationY = (1f - enter) * 30f }
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(CE.Navy, CE.NavyLight, CE.Teal)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(2.dp, CE.Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initialsOf(initialsFrom), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// A statistic card with an icon tile. The number counts up from zero.
@Composable
fun IconStat(
    icon: ImageVector,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = CE.Teal,
    soft: Color = CE.Mint
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val shown by animateIntAsState(
        targetValue = if (go) value else 0,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "stat"
    )
    CourtCard(modifier.heightIn(min = 96.dp).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(soft),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("%02d".format(shown), color = CE.Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(label, color = CE.Muted, fontSize = 13.sp, lineHeight = 16.sp)
            }
        }
    }
}
