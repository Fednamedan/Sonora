package com.example.sonora.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

/** Акцент, пока ничего не играет: тёплый янтарный, как свечение лампового усилителя. */
val DefaultAccent = Color(0xFFF2A93B)

private val DarkColors = darkColorScheme(
    primary = DefaultAccent,
    onPrimary = Color(0xFF14151A),
    background = Color(0xFF0F1013),
    onBackground = Color(0xFFECECF0),
    surface = Color(0xFF0F1013),
    onSurface = Color(0xFFECECF0),
    surfaceVariant = Color(0xFF24262C),
    onSurfaceVariant = Color(0xFF9C9EA8),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF131418),
    surfaceContainer = Color(0xFF17181C),
    surfaceContainerHigh = Color(0xFF1E2025),
    surfaceContainerHighest = Color(0xFF282A31),
    outline = Color(0xFF6B6D77),
    outlineVariant = Color(0xFF34363D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A5B00),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF15161A),
    surface = Color(0xFFF5F5F7),
    onSurface = Color(0xFF15161A),
    surfaceVariant = Color(0xFFE3E4E9),
    onSurfaceVariant = Color(0xFF5B5D68),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F0F3),
    surfaceContainer = Color(0xFFEAEAEE),
    surfaceContainerHigh = Color(0xFFE4E4E9),
    surfaceContainerHighest = Color(0xFFDDDDE3),
    outline = Color(0xFF787A85),
    outlineVariant = Color(0xFFC8C9D1),
)

private val Display = FontFamily.Serif

/** Заголовки с засечками (как на конверте пластинки), всё остальное без засечек. */
private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(
            fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp,
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
        ),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Приводит цвет обложки к читаемому акценту: не слишком тёмный, не блёклый. */
fun tuneAccent(seed: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.95f)
    hsl[2] = if (dark) hsl[2].coerceIn(0.62f, 0.78f) else hsl[2].coerceIn(0.30f, 0.44f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Тема, акцент которой следует за играющим треком. [accentSeed] — цвет обложки
 * (или [DefaultAccent], если ничего не играет); смена цвета идёт плавно.
 */
@Composable
fun SonoraTheme(accentSeed: Color, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val target = remember(accentSeed, dark) { tuneAccent(accentSeed, dark) }
    val accent by animateColorAsState(target, tween(600), label = "accent")
    val base = if (dark) DarkColors else LightColors

    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF14151A) else Color.White
    val container = lerp(base.surface, accent, if (dark) 0.26f else 0.20f)
    val scheme = base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = base.onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = base.onSurface,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
