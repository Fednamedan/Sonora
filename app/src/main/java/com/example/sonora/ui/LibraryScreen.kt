package com.example.sonora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonora.data.Playlist
import com.example.sonora.data.Track

private val TAB_TITLES = listOf("Избранное", "Плейлисты", "Загрузки", "История", "На телефоне")

@Composable
fun LibraryScreen() {
    val c = LocalContainer.current
    val lib by c.store.state.collectAsState()
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    val openId = lib.playlists.firstOrNull { it.id == openPlaylist }?.id

    AnimatedContent(
        targetState = openId,
        transitionSpec = { pushPop(targetState != null) },
        label = "library",
    ) { id ->
        val playlist = lib.playlists.firstOrNull { it.id == id }
        if (playlist != null) {
            BackHandler { openPlaylist = null }
            PlaylistScreen(playlist, onBack = { openPlaylist = null })
        } else {
            LibraryHome(onOpenPlaylist = { openPlaylist = it })
        }
    }
}

@Composable
private fun LibraryHome(onOpenPlaylist: (String) -> Unit) {
    val c = LocalContainer.current
    val lib by c.store.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val ui = rememberTrackUi()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Библиотека")
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            divider = {},
        ) {
            TAB_TITLES.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        Crossfade(targetState = tab, label = "libraryTab") { current ->
            when (current) {
                0 -> TrackTab(
                    tracks = lib.favoriteTracks(),
                    ui = ui,
                    emptyTitle = "Здесь будут ваши любимые треки",
                    emptyHint = "Нажмите на сердечко в плеере или в меню трека",
                )

                1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        OutlinedButton(
                            onClick = { creating = true },
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Новый плейлист")
                        }
                    }
                    if (lib.playlists.isEmpty()) {
                        item { EmptyState("Плейлистов пока нет", "Создайте первый и добавляйте в него треки из меню") }
                    } else {
                        items(lib.playlists, key = { it.id }) { p ->
                            PlaylistRow(
                                playlist = p,
                                cover = lib.playlistTracks(p).firstOrNull()?.artworkUrl,
                                modifier = Modifier.animateItem(),
                            ) { onOpenPlaylist(p.id) }
                        }
                    }
                }

                2 -> TrackTab(
                    tracks = lib.downloadedTracks(),
                    ui = ui,
                    emptyTitle = "Скачанных треков нет",
                    emptyHint = "Скачайте треки из меню или с экрана плеера, и они будут играть без интернета",
                )

                3 -> TrackTab(
                    tracks = lib.historyTracks(),
                    ui = ui,
                    emptyTitle = "История пуста",
                    emptyHint = "Здесь появятся треки, которые вы послушали",
                    footer = { TextButton(onClick = { c.store.clearHistory() }) { Text("Очистить историю") } },
                )

                else -> PhoneTab(ui)
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = { name ->
                val id = c.store.createPlaylist(name)
                creating = false
                onOpenPlaylist(id)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
internal fun TrackTab(
    tracks: List<Track>,
    ui: TrackUi,
    emptyTitle: String,
    emptyHint: String,
    footer: (@Composable () -> Unit)? = null,
) {
    val c = LocalContainer.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (tracks.isEmpty()) {
            item { EmptyState(emptyTitle, emptyHint) }
        } else {
            item { PlayButtons(tracks) }
            trackItems(tracks, ui, onPlay = { c.player.play(tracks, it) })
            if (footer != null) item { Box(Modifier.padding(horizontal = 8.dp)) { footer() } }
        }
    }
}

@Composable
private fun PlayButtons(tracks: List<Track>) {
    val c = LocalContainer.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { c.player.play(tracks, 0) }) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Слушать")
        }
        FilledTonalButton(onClick = { c.player.playShuffled(tracks) }) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Вперемешку")
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    cover: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .bounceClick(onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) {
            Artwork(cover, Modifier.size(56.dp), corner = 12.dp)
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                trackCountText(playlist.trackIds.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaylistScreen(playlist: Playlist, onBack: () -> Unit) {
    val c = LocalContainer.current
    val lib by c.store.state.collectAsState()
    val ui = rememberTrackUi()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val tracks = lib.playlistTracks(playlist)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            DetailHeader(playlist.name, onBack, subtitle = trackCountText(tracks.size))
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { c.player.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Слушать")
                }
                FilledTonalButton(onClick = { c.player.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Вперемешку")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Переименовать")
                }
                IconButton(onClick = { deleting = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Удалить плейлист")
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState("Плейлист пуст", "Добавляйте треки через меню «⋮» рядом с любым треком") }
        } else {
            trackItems(
                tracks, ui,
                onPlay = { c.player.play(tracks, it) },
                trailing = { t ->
                    {
                        IconButton(onClick = { c.store.removeFromPlaylist(playlist.id, t.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Убрать из плейлиста")
                        }
                    }
                },
            )
        }
    }

    if (renaming) {
        NameDialog(
            title = "Переименовать",
            initial = playlist.name,
            confirmLabel = "Сохранить",
            onConfirm = { name ->
                c.store.renamePlaylist(playlist.id, name)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${playlist.name}» будет удалён. Сами треки останутся в приложении.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onBack()
                    c.store.deletePlaylist(playlist.id)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } },
        )
    }
}

internal fun trackCountText(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod100 in 11..14 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$n $word"
}
