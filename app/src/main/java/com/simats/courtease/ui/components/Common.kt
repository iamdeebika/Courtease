package com.simats.courtease.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.courtease.ui.theme.CE
import kotlinx.coroutines.delay

// ---------- motion helpers (shared inside this package) ----------

// Hands out a small delay to each item that appears in a burst, so cards arrive one after another.
private class Stagger {
    private var last = 0L
    private var n = 0

    fun next(): Int {
        val now = System.nanoTime()
        if (now - last > 350_000_000L) n = 0
        last = now
        val index = n
        n = (n + 1).coerceAtMost(7)
        return index
    }
}

private val LocalStagger = staticCompositionLocalOf { Stagger() }

// Fades and slides an item in the first time it appears.
@Composable
private fun Modifier.cardEntrance(): Modifier {
    val stagger = LocalStagger.current
    val index = remember { stagger.next() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "enter"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 36f
    }
}

// A value that dips slightly while something is pressed, then springs back.
@Composable
internal fun pressScale(source: MutableInteractionSource, amount: Float): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) amount else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    val base = Color(0xFFE3EAEE)
    val glow = Color(0xFFF4F8FA)
    return Brush.linearGradient(
        colors = listOf(base, glow, base),
        start = Offset(shift * 1400f - 400f, 0f),
        end = Offset(shift * 1400f, 0f)
    )
}

// ---------- scaffold ----------

@Composable
fun CourtTopBar(onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(Brush.horizontalGradient(listOf(CE.Navy, CE.NavyLight)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        } else {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Text("⚖", color = Color.White, fontSize = 20.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "CourtEase", color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val badge = if (description == "Alerts") {
        com.simats.courtease.data.Store.unread(com.simats.courtease.data.Session.userId)
    } else {
        0
    }
    val badgeScale by animateFloatAsState(
        targetValue = if (badge > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "badge"
    )
    Box(Modifier.size(40.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, description, tint = Color.White)
        }
        if (badgeScale > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(18.dp)
                    .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale }
                    .clip(CircleShape)
                    .background(CE.Red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 9) "9+" else badge.toString(),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
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
    val stagger = remember { Stagger() }
    CompositionLocalProvider(LocalStagger provides stagger) {
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
}

// ---------- text ----------

@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.cardEntrance(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(50)).background(CE.Teal))
            Spacer(Modifier.width(8.dp))
            Text(
                eyebrow, color = CE.Teal, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
            )
        }
        Text(title, color = CE.Navy, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        if (subtitle != null) Text(subtitle, color = CE.Muted, fontSize = 16.sp, lineHeight = 23.sp)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = CE.Navy, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
    val shape = RoundedCornerShape(22.dp)
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, 0.98f)
    val base = modifier
        .cardEntrance()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(3.dp, shape, ambientColor = CE.Shadow, spotColor = CE.Shadow)
        .fillMaxWidth()
        .clip(shape)
        .background(color)
        .border(1.dp, borderColor, shape)
    val tappable = if (onClick != null) {
        base.clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
    } else {
        base
    }
    Column(tappable.padding(18.dp), content = content)
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
    val bg by animateColorAsState(
        targetValue = if (selected) CE.Mint else CE.Chip, animationSpec = tween(200), label = "chipBg"
    )
    val edge by animateColorAsState(
        targetValue = if (selected) CE.Teal else CE.Teal.copy(alpha = 0f),
        animationSpec = tween(200), label = "chipEdge"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) CE.Teal else CE.Navy, animationSpec = tween(200), label = "chipFg"
    )
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, 0.95f)
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text, color = fg, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun InfoBanner(text: String) {
    Row(
        Modifier.cardEntrance().fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Mint).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = CE.Teal)
        Spacer(Modifier.width(12.dp))
        Text(text, color = CE.Teal, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
fun WarnBanner(title: String, text: String, onClick: (() -> Unit)? = null) {
    val base = Modifier.cardEntrance().fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CE.Amber)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(m.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = CE.AmberText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = CE.AmberText, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, fraction: Float? = null) {
    // Numbers such as "02" count up from zero when the card first appears.
    val number = if (value.isNotEmpty() && value.all { it.isDigit() }) value.toIntOrNull() else null
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val counted by animateIntAsState(
        targetValue = if (number != null && go) number else 0,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "count"
    )
    val shown = if (number != null) counted.toString().padStart(value.length, '0') else value

    CourtCard(modifier) {
        Text(shown, color = if (fraction != null) CE.Teal else CE.Navy, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            label, color = if (fraction != null) CE.Navy else CE.Muted, fontSize = 16.sp,
            fontWeight = if (fraction != null) FontWeight.SemiBold else FontWeight.Normal
        )
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            ThinProgress(fraction)
        }
    }
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val animated by animateFloatAsState(
        targetValue = if (go) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "progress"
    )
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(CE.Track)) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(CE.Teal, CE.TealLight)))
        )
    }
}

@Composable
fun ToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CourtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(CE.Mint),
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
private fun StageBar(active: Boolean, modifier: Modifier) {
    val color by animateColorAsState(
        targetValue = if (active) CE.Teal else CE.Track, animationSpec = tween(500), label = "stage"
    )
    Box(modifier.height(5.dp).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun StageProgress(stage: String, stages: List<String>) {
    val current = stages.indexOf(stage).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stages.forEachIndexed { i, _ -> StageBar(i <= current, Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { i, s ->
            Text(
                s, modifier = Modifier.weight(1f), fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (i == current) CE.Teal else CE.Muted,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    val brush = shimmerBrush()
    CourtCard {
        Box(Modifier.fillMaxWidth(0.5f).height(20.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(8.dp)).background(brush))
    }
}

@Composable
fun EmptyCard(title: String, text: String) {
    // Screens that say "Loading" get shimmering placeholder bars instead of plain text.
    if (title.equals("Loading", ignoreCase = true)) {
        LoadingCard()
        return
    }
    CourtCard {
        Text(title, color = CE.Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        BodyText(text)
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun SmallCircle(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}