package com.example.sonora.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.sonora.data.Genres
import com.example.sonora.data.RadioSeed
import com.example.sonora.data.Track
import com.example.sonora.playback.PlayerController
import com.example.sonora.playback.PlayerState
import com.example.sonora.util.formatDuration

/** Плавающий мини-плеер над нижней навигацией. Окрашивается цветом обложки. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalContainer.current.player
    val s by player.state.collectAsState()
    val track = s.current ?: return
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = lerp(scheme.surfaceContainerHigh, scheme.primary, 0.24f),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .bounceClick(onOpen),
    ) {
        Column {
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artworkUrl, Modifier.size(44.dp), corner = 12.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = player::togglePlay) {
                    if (s.isBuffering && !s.isPlaying) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Crossfade(targetState = s.isPlaying, label = "miniPlay") { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Пауза" else "Играть",
                            )
                        }
                    }
                }
                IconButton(onClick = player::next) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий")
                }
            }
            val fraction = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = scheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

/** Полноэкранный плеер: весь экран окрашен цветом обложки, обложка «дышит» вместе с музыкой. */
@Composable
fun PlayerScreen(onClose: () -> Unit, onAddToPlaylist: (Track) -> Unit) {
    val c = LocalContainer.current
    val player = c.player
    val s by player.state.collectAsState()
    val lib by c.store.state.collectAsState()
    val downloadProgress by c.downloads.progress.collectAsState()
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }
    var vinyl by rememberSaveable { mutableStateOf(true) }
    val track = s.current
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()

    // Фон: цвет акцента (он уже взят из обложки) стекает сверху в нейтральный
    val tint = scheme.primary.copy(alpha = if (dark) 0.22f else 0.12f)
    val backdrop = Brush.verticalGradient(0f to tint, 0.78f to scheme.background, 1f to scheme.background)

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        if (track == null) {
            LaunchedEffect(Unit) { onClose() }
            return@Surface
        }
        val isFavorite = track.id in lib.favoriteSet
        val isDownloaded = track.id in lib.downloadSet
        val progress = downloadProgress[track.id]

        // Размытая обложка как «аура» экрана (blur работает с Android 12+, на старых просто градиент)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Artwork(
                track.artworkUrl,
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .graphicsLayer { alpha = if (dark) 0.55f else 0.40f; scaleX = 1.3f; scaleY = 1.3f }
                    .blur(70.dp),
                corner = 0.dp,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(backdrop)
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = { if (dy > 220f) onClose() },
                        onVerticalDrag = { _, d -> dy += d },
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Верхняя панель
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть")
                }
                val radio = s.radio
                Text(
                    text = if (radio != null) radioTitle(radio) else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Очередь",
                        tint = if (showQueue) scheme.primary else scheme.onSurface,
                    )
                }
            }

            // Обложка или очередь
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                AnimatedContent(
                    targetState = showQueue,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "artOrQueue",
                ) { queueMode ->
                    if (queueMode) {
                        QueueList(s, player, Modifier.fillMaxSize())
                    } else {
                        // Обложка «дышит»: на паузе чуть уменьшается, при игре расправляется
                        val breathe by animateFloatAsState(
                            targetValue = if (s.isPlaying) 1f else 0.88f,
                            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                            label = "breathe",
                        )
                        AnimatedContent(
                            targetState = track,
                            transitionSpec = {
                                (fadeIn(tween(350)) + scaleIn(initialScale = 0.92f, animationSpec = tween(350))) togetherWith
                                    fadeOut(tween(200))
                            },
                            label = "artwork",
                        ) { t ->
                            val artMod = Modifier
                                .size(side)
                                .pointerInput(t.id) {
                                    detectTapGestures(
                                        onTap = { vinyl = !vinyl },
                                        onDoubleTap = {
                                            if (t.id !in c.store.state.value.favoriteSet) c.store.toggleFavorite(t)
                                            burst++
                                        },
                                    )
                                }
                                .pointerInput(t.id) {
                                    var dx = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { dx = 0f },
                                        onDragEnd = {
                                            if (dx > 120f) player.previous() else if (dx < -120f) player.next()
                                        },
                                        onHorizontalDrag = { _, d -> dx += d },
                                    )
                                }
                                .graphicsLayer {
                                    scaleX = breathe
                                    scaleY = breathe
                                }
                            if (vinyl) {
                                VinylDisc(t.artworkUrl, s.isPlaying, artMod)
                            } else {
                                Artwork(
                                    t.artworkUrl,
                                    artMod.shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        ambientColor = scheme.primary,
                                        spotColor = scheme.primary,
                                    ),
                                    corner = 24.dp,
                                )
                            }
                        }
                    }
                }
                HeartBurst(burst)
            }

            // Название и лайк
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                    modifier = Modifier.weight(1f),
                    label = "titles",
                ) { t ->
                    Column {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AnimatedHeart(
                    favorite = isFavorite,
                    onToggle = { c.store.toggleFavorite(track) },
                    iconSize = 28.dp,
                )
            }

            // Перемотка
            var drag by remember { mutableStateOf<Float?>(null) }
            val duration = s.durationMs.coerceAtLeast(1L)
            val fraction = drag ?: (s.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            WaveSeekBar(
                value = fraction,
                seed = track.id,
                playing = s.isPlaying,
                onChange = { drag = it },
                onFinish = {
                    drag?.let { player.seekTo((it * duration).toLong()) }
                    drag = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration((fraction * duration).toLong() / 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    formatDuration(s.durationMs / 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // Управление
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (s.shuffle) scheme.primary else scheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(76.dp)) {
                    if (s.isBuffering && !s.isPlaying) {
                        CircularProgressIndicator(
                            Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = scheme.onPrimary,
                        )
                    } else {
                        Crossfade(targetState = s.isPlaying, animationSpec = tween(180), label = "playPause") { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Пауза" else "Играть",
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Повтор",
                        tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) scheme.primary
                        else scheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            // Дополнительные действия
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = {
                    when {
                        isDownloaded -> c.downloads.remove(track)
                        progress == null -> c.downloads.download(track)
                    }
                }) {
                    when {
                        progress != null -> CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )

                        isDownloaded -> Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = "Удалить загрузку",
                            tint = scheme.primary,
                        )

                        else -> Icon(Icons.Rounded.Download, contentDescription = "Скачать для офлайна")
                    }
                }
                IconButton(onClick = { onAddToPlaylist(track) }) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "В плейлист")
                }
                IconButton(onClick = { player.startRadio(RadioSeed.OfTrack(track)) }) {
                    Icon(Icons.Rounded.Radio, contentDescription = "Радио по треку")
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = "Таймер сна",
                        tint = if (s.sleepEndsAt != null) scheme.primary else scheme.onSurface,
                    )
                }
            }
        }
    }

    if (showSleep) {
        SleepDialog(
            endsAt = s.sleepEndsAt,
            onPick = { player.setSleepTimer(it) },
            onDismiss = { showSleep = false },
        )
    }
}

private fun radioTitle(seed: RadioSeed): String = when (seed) {
    is RadioSeed.Wave -> "Моя волна"
    is RadioSeed.OfGenre -> "Радио: ${Genres.label(seed.genre)}"
    is RadioSeed.OfTrack -> "Радио по треку «${seed.track.title}»"
}

@Composable
private fun QueueList(s: PlayerState, player: PlayerController, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (s.queueIndex - 1).coerceAtLeast(0))
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Очередь", style = MaterialTheme.typography.titleLarge)
            Text(
                trackCountText(s.queue.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(s.queue, key = { i, t -> "${t.id}#$i" }) { index, t ->
                val current = index == s.queueIndex
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clickable { player.skipTo(index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.artworkUrl, Modifier.size(44.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            t.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    if (!current) {
                        IconButton(onClick = { player.removeFromQueue(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Убрать из очереди")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepDialog(endsAt: Long?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column {
                if (endsAt != null) {
                    val left = ((endsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1L)
                    Text(
                        "Музыка остановится примерно через $left мин",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    TextButton(onClick = { onPick(minutes); onDismiss() }) { Text("$minutes минут") }
                }
                if (endsAt != null) {
                    TextButton(onClick = { onPick(null); onDismiss() }) { Text("Выключить таймер") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Сердце вспыхивает по центру обложки при двойном тапе. */
@Composable
private fun HeartBurst(trigger: Int) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.4f) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        fade.snapTo(1f)
        scale.snapTo(0.4f)
        scale.animateTo(1.2f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        fade.animateTo(0f, tween(350))
    }
    if (fade.value > 0f) {
        Icon(
            androidx.compose.material.icons.Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = fade.value
                },
        )
    }
}
