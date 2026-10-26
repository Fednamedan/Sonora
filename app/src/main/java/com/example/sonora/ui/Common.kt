package com.example.sonora.ui

import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.sonora.AppContainer
import com.example.sonora.data.Genre
import com.example.sonora.data.Track
import com.example.sonora.util.formatDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }

/** Открывает меню действий для трека (показывается в MainScreen). */
val LocalOpenTrackMenu = staticCompositionLocalOf<(Track) -> Unit> { { _: Track -> } }

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Что нужно списку треков, чтобы подсветить играющий, лайки и загрузки. */
@Immutable
class TrackUi(
    val currentId: String?,
    val isPlaying: Boolean,
    val favorites: Set<String>,
    val downloads: Set<String>,
    val progress: Map<String, Float>,
)

@Composable
fun rememberTrackUi(): TrackUi {
    val c = LocalContainer.current
    // Берём только id и «играет ли», чтобы списки не перерисовывались каждые полсекунды
    val now by remember(c) {
        c.player.state.map { it.current?.id to it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = null to false)
    val lib by c.store.state.collectAsState()
    val progress by c.downloads.progress.collectAsState()
    return TrackUi(now.first, now.second, lib.favoriteSet, lib.downloadSet, progress)
}

@Composable
fun TrackRow(
    track: Track,
    ui: TrackUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val openMenu = LocalOpenTrackMenu.current
    val isCurrent = track.id == ui.currentId
    val scheme = MaterialTheme.colorScheme
    val rowColor by animateColorAsState(
        targetValue = if (isCurrent) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(300),
        label = "rowColor",
    )

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize())
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(ui.isPlaying, Color.White, Modifier.size(20.dp))
                }
            }
        }
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
                color = if (isCurrent) scheme.primary else scheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.id in ui.downloads) {
                    Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = "Скачано",
                        modifier = Modifier.size(14.dp),
                        tint = scheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (track.id in ui.favorites) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "В избранном",
                tint = scheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(15.dp),
            )
        }
        if (track.durationSec > 0) {
            Text(
                formatDuration(track.durationSec.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
        IconButton(onClick = { openMenu(track) }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Меню трека")
        }
    }
}

/** Добавляет в LazyColumn строки треков. [onPlay] получает индекс нажатого трека. */
fun LazyListScope.trackItems(
    tracks: List<Track>,
    ui: TrackUi,
    onPlay: (Int) -> Unit,
    trailing: ((Track) -> (@Composable () -> Unit))? = null,
) {
    itemsIndexed(tracks, key = { index, t -> "${t.id}#$index" }) { index, t ->
        TrackRow(
            track = t,
            ui = ui,
            onClick = { onPlay(index) },
            modifier = Modifier.animateItem().staggeredAppear(index),
            trailing = trailing?.invoke(t),
        )
    }
}

/** Плитка жанра: тональный фон, крупное название. */
@Composable
fun GenreTile(genre: Genre, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = genreColor(genre.api, dark)
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(base, androidx.compose.ui.graphics.lerp(base, MaterialTheme.colorScheme.primary, 0.30f)),
    )
    Box(
        modifier
            .bounceClick(onClick)
            .height(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(46.dp)
                .offset(x = 10.dp, y = (-8).dp)
                .rotate(-14f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        )
        Text(
            genre.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
fun DetailHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Скелетон списка треков: показывается, пока каталог загружается. */
@Composable
fun TrackSkeleton(count: Int = 8) {
    Column(Modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .shimmer()
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.32f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmer()
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
