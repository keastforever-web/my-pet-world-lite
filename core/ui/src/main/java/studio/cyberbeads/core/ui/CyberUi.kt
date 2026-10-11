package studio.cyberbeads.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cyberbeads.core.model.BeadPattern
import kotlin.math.*

val Ink: Color @Composable get() = MaterialTheme.colorScheme.background
val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
val Mint: Color @Composable get() = MaterialTheme.colorScheme.primary
val Lavender: Color @Composable get() = MaterialTheme.colorScheme.secondary
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Border: Color @Composable get() = MaterialTheme.colorScheme.outline

@Composable fun CyberTheme(style: String = "pink", content: @Composable () -> Unit) {
    val scheme = when(style) {
        "night" -> darkColorScheme(primary=Color(0xffefb0d2),onPrimary=Color(0xff43243a),background=Color(0xff1c1926),surface=Color(0xff282331),onSurface=Color(0xfff8eaf3),onBackground=Color(0xfff8eaf3),onSurfaceVariant=Color(0xffc6b7cd),outline=Color(0xff62546b))
        "mint" -> lightColorScheme(primary=Color(0xff267869),onPrimary=Color.White,secondary=Color(0xff795ca5),background=Color(0xfff0fbf6),surface=Color.White,onBackground=Color(0xff24433c),onSurface=Color(0xff24433c),onSurfaceVariant=Color(0xff586b63),outline=Color(0xffc1ded3))
        else -> lightColorScheme(primary=Color(0xffb7437c),onPrimary=Color.White,secondary=Color(0xff8061b2),background=Color(0xfffff5fa),surface=Color.White,onBackground=Color(0xff453342),onSurface=Color(0xff453342),surfaceVariant=Color(0xfffce6f0),onSurfaceVariant=Color(0xff806875),outline=Color(0xffefd0df))
    }
    MaterialTheme(colorScheme = scheme,
        shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}

@Composable fun SectionLabel(kicker: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(kicker, color = Mint, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
    }
}

@Composable fun CyberCard(modifier: Modifier = Modifier, contentPadding: androidx.compose.ui.unit.Dp = 18.dp, spacing: androidx.compose.ui.unit.Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Border, RoundedCornerShape(22.dp)).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Mint, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable fun ColorDot(color: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick, modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Border else Panel)) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(color)).border(1.dp, Color.White.copy(alpha = .3f), CircleShape))
        Spacer(Modifier.width(7.dp)); Text(label, fontSize = 12.sp)
    }
}

fun demoPattern(): BeadPattern {
    val rows = listOf(
        "................", "...aa......aa...", "...aba....aba...", "...abbaaaabba...",
        "..aaaaaaaaaaaa..", "..aaaccaaccaaa..", "..aaaccaaccaaa..", "..aaaaaaaaaaaa..",
        "..abaaaaaaaaba..", "...aaaaccaaaa...", "....aaaaaaa.....", "...abaaaaaba....",
        "...abaaaaaba....", "....aaaaaaa..aa.", "....aa...aaaaa..", "................")
    return BeadPattern(16, 16, listOf(0xff9af5ce.toInt(), 0xffbaa5ff.toInt(), 0xff243b47.toInt()),
        rows.flatMap { row -> row.map { when (it) { 'a' -> 0; 'b' -> 1; 'c' -> 2; else -> -1 } } })
}
