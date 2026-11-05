package com.simats.courtease.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay

// A moving light band, used for loading placeholders.
fun Modifier.fxShimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    val base = Color(0xFFE3EAEE)
    val glow = Color(0xFFF4F8FA)
    background(
        Brush.linearGradient(
            colors = listOf(base, glow, base),
            start = Offset(shift * 1400f - 400f, 0f),
            end = Offset(shift * 1400f, 0f)
        )
    )
}

// A grey bar that shimmers. Use it where text is still loading.
@Composable
fun FxSkeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, corner: Dp = 8.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(corner)).fxShimmer())
}

// A card-shaped loading placeholder, used instead of the plain "Loading..." text.
@Composable
fun FxSkeletonCard(lines: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FxSkeleton(Modifier.fillMaxWidth(0.55f), 20.dp)
        repeat(lines) { i ->
            FxSkeleton(Modifier.fillMaxWidth(if (i == lines - 1) 0.7f else 1f), 14.dp)
        }
    }
}

// Fades and slides content in. Give each item in a list its own index, and they appear one after another.
@Composable
fun FxFadeIn(index: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 8) * 60L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(380)) +
                slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 8 }
    ) {
        content()
    }
}

// A number that counts up from 0 when it first appears.
@Composable
fun fxCount(target: Int, millis: Int = 800): Int {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val value by animateIntAsState(
        targetValue = if (go) target else 0,
        animationSpec = tween(millis, easing = FastOutSlowInEasing),
        label = "count"
    )
    return value
}

// Makes something shrink slightly while pressed, then spring back. It replaces the ripple.
fun Modifier.fxBounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this
        .scale(scale)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

// A green circle with a tick that pops in. Use it after filing or registering.
@Composable
fun FxSuccess(visible: Boolean = true, size: Dp = 64.dp) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(CE.Teal),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, fontSize = (size.value * 0.5f).sp)
        }
    }
}

// A progress bar that slides to its value, with fraction between 0 and 1.
@Composable
fun FxBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(CE.Track)) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(CE.Teal)
        )
    }
}