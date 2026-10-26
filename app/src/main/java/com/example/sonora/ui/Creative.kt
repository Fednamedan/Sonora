package com.example.sonora.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlin.math.PI
import kotlin.math.sin

/** Виниловая пластинка: крутится, пока играет музыка, плавно разгоняется и тормозит. Обложка — этикетка. */
@Composable
fun VinylDisc(artworkUrl: String?, playing: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        speed.animateTo(if (playing) 1f else 0f, tween(if (playing) 900 else 1800))
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) {
                    val dt = (t - last) / 1_000_000_000f
                    angle = (angle + dt * speed.value * 110f) % 360f
                }
                last = t
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.shadow(24.dp, CircleShape, ambientColor = scheme.primary, spotColor = scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF0C0C0F), radius = r)
                var f = 0.40f
                while (f < 0.98f) {
                    drawCircle(Color.White.copy(alpha = 0.045f), radius = r * f, style = Stroke(1.2f))
                    f += 0.032f
                }
            }
            Box(
                Modifier
                    .fillMaxSize(0.38f)
                    .clip(CircleShape),
            ) {
                Artwork(artworkUrl, Modifier.fillMaxSize(), corner = 0.dp)
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF0C0C0F), radius = r * 0.035f)
                drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 0.38f, style = Stroke(2f))
            }
        }
        // Блик света не вращается вместе с пластинкой
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent,
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent,
                    ),
                    center = center,
                ),
                radius = size.minDimension / 2f,
            )
        }
    }
}

/** Перемотка в виде звуковой дорожки: прослушанная часть окрашена акцентом, рядом с курсором столбики пульсируют. */
@Composable
fun WaveSeekBar(
    value: Float,
    seed: String,
    playing: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = 56
    val heights = remember(seed) {
        val rnd = java.util.Random(seed.hashCode().toLong())
        var prev = 0.5f
        FloatArray(bars) {
            prev = prev * 0.5f + rnd.nextFloat() * 0.5f
            (0.16f + prev * 1.25f).coerceAtMost(1f)
        }
    }
    val onChangeS by rememberUpdatedState(onChange)
    val onFinishS by rememberUpdatedState(onFinish)
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseValue",
    )
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    onChangeS((o.x / size.width).coerceIn(0f, 1f))
                    onFinishS()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onChangeS((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { onFinishS() },
                    onDragCancel = { onFinishS() },
                    onHorizontalDrag = { change, _ -> onChangeS((change.position.x / size.width).coerceIn(0f, 1f)) },
                )
            }
    ) {
        val slot = size.width / bars
        val bw = slot * 0.58f
        val playhead = value * bars
        for (i in 0 until bars) {
            var h = heights[i]
            val dist = kotlin.math.abs(i - playhead)
            if (playing && dist < 4f) {
                h *= 1f + 0.28f * (1f - dist / 4f) * sin(2.0 * PI * (pulse.value + i * 0.17)).toFloat()
            }
            val bh = (h.coerceAtMost(1.15f) * size.height).coerceAtLeast(bw)
            drawRoundRect(
                color = if (i + 0.5f <= playhead) active else idle,
                topLeft = Offset(i * slot + (slot - bw) / 2f, (size.height - bh) / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
    }
}

/** Живое «северное сияние» за интерфейсом: два медленных пятна цвета акцента. */
@Composable
fun Modifier.aurora(): Modifier {
    val t = rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraValue",
    )
    val color = MaterialTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()
    val a = if (dark) 0.20f else 0.12f
    return drawBehind {
        val d = t.value
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * (0.1f + 0.7f * d), size.height * 0.16f),
                radius = size.width * 0.95f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = a * 0.6f), Color.Transparent),
                center = Offset(size.width * (0.95f - 0.6f * d), size.height * (0.78f + 0.1f * d)),
                radius = size.width * 0.85f,
            )
        )
    }
}
