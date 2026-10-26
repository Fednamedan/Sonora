package com.example.sonora.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- переходы между экранами ----------

/** Вход «вглубь» (push) и возврат (pop): лёгкий сдвиг и затухание. */
fun <S> AnimatedContentTransitionScope<S>.pushPop(push: Boolean): ContentTransform =
    if (push) {
        (slideInHorizontally { it / 6 } + fadeIn(tween(260))) togetherWith
            (slideOutHorizontally { -it / 10 } + fadeOut(tween(160)))
    } else {
        (slideInHorizontally { -it / 10 } + fadeIn(tween(260))) togetherWith
            (slideOutHorizontally { it / 6 } + fadeOut(tween(160)))
    }

// ---------- нажатия ----------

/** Нажатие с пружинкой: элемент слегка «вдавливается». */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

// ---------- загрузка ----------

/** Бегущий блик для скелетонов вместо крутилки. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    this.drawBehind {
        val w = size.width
        val start = (shift.value * 2f - 0.6f) * w
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(base, highlight, base),
                startX = start,
                endX = start + w * 0.6f,
            )
        )
    }
}

// ---------- эквалайзер ----------

/** Три столбика: пляшут, пока музыка играет, и замирают на паузе. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a = transition.animateFloat(
        0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a",
    )
    val b = transition.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "b",
    )
    val c = transition.animateFloat(
        0.2f, 0.95f, infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Reverse), label = "c",
    )
    Canvas(modifier) {
        val barW = size.width / 5f
        val heights = if (playing) listOf(a.value, b.value, c.value) else listOf(0.3f, 0.5f, 0.4f)
        heights.forEachIndexed { i, h ->
            val barH = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2f * barW, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

// ---------- сердце ----------

/** Сердце с пружинкой и вибро-откликом при добавлении в избранное. */
@Composable
fun AnimatedHeart(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(favorite) {
        if (first) {
            first = false
        } else if (favorite) {
            scale.animateTo(1.4f, tween(90))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        modifier = modifier,
    ) {
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (favorite) "Убрать из избранного" else "В избранное",
            tint = if (favorite) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

// ---------- цвет обложки ----------

private val colorCache = LruCache<String, Int>(128)

/**
 * Доминирующий цвет обложки. Пока новый цвет считается, остаётся предыдущий,
 * поэтому смена трека не даёт мигания.
 */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url == null) {
            color = fallback
            return@LaunchedEffect
        }
        val cached = colorCache.get(url)
        if (cached != null) {
            color = Color(cached)
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) { extractColor(context, url) }
        if (extracted != null) {
            colorCache.put(url, extracted)
            color = Color(extracted)
        }
    }
    return color
}

private suspend fun extractColor(context: Context, url: String): Int? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(96)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return null
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        swatch?.rgb
    } catch (e: Exception) {
        null
    }
}

// ---------- цвета жанров ----------

/** Спокойный тональный цвет плитки жанра, стабильный для каждого жанра. */
fun genreColor(api: String, dark: Boolean): Color {
    val hue = (api.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat()
    return Color.hsl(hue, 0.38f, if (dark) 0.26f else 0.86f)
}


/** Мягкое появление элементов списка: снизу вверх с небольшой задержкой по номеру (только первые строки). */
fun Modifier.staggeredAppear(index: Int): Modifier = composed {
    val progress = remember { Animatable(if (index < 12) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            kotlinx.coroutines.delay(index * 45L)
            progress.animateTo(1f, tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 48f
    }
}
