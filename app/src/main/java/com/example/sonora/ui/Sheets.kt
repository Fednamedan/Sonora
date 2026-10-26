package com.example.sonora.ui

import androidx.compose.material.icons.rounded.Share
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonora.data.RadioSeed
import com.example.sonora.data.Track

/** Меню действий над треком. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val lib by c.store.state.collectAsState()
    val isFavorite = track.id in lib.favoriteSet
    val isDownloaded = track.id in lib.downloadSet
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artworkUrl, Modifier.size(56.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            MenuItem(Icons.Rounded.SkipNext, "Играть следующим") {
                c.player.playNext(track)
                onDismiss()
            }
            MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Добавить в очередь") {
                c.player.addToQueue(track)
                onDismiss()
            }
            MenuItem(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFavorite) "Убрать из избранного" else "В избранное",
            ) {
                c.store.toggleFavorite(track)
                onDismiss()
            }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Добавить в плейлист") {
                onAddToPlaylist(track)
                onDismiss()
            }
            if (isDownloaded) {
                MenuItem(Icons.Rounded.Delete, "Удалить загрузку") {
                    c.downloads.remove(track)
                    onDismiss()
                }
            } else {
                MenuItem(Icons.Rounded.Download, "Скачать для офлайна") {
                    c.downloads.download(track)
                    c.messages.tryEmit("Скачиваю: ${track.title}")
                    onDismiss()
                }
            }
            MenuItem(Icons.Rounded.Radio, "Радио по этому треку") {
                c.player.startRadio(RadioSeed.OfTrack(track))
                onDismiss()
            }
            val link = track.permalink
            if (link != null) {
                MenuItem(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в браузере") {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } catch (e: ActivityNotFoundException) {
                        c.messages.tryEmit("Не нашёл приложение для открытия ссылки")
                    }
                    onDismiss()
                }
                MenuItem(Icons.Rounded.Share, "Поделиться") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n$link")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                    onDismiss()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Выбор плейлиста для трека (с возможностью создать новый). */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val lib by c.store.state.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = { name ->
                c.store.createPlaylist(name, track)
                c.messages.tryEmit("Добавлено в «${name.trim().ifEmpty { "Без названия" }}»")
                onDismiss()
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Новый плейлист", color = MaterialTheme.colorScheme.primary)
                }
                lib.playlists.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val added = c.store.addToPlaylist(p.id, track)
                                c.messages.tryEmit(
                                    if (added) "Добавлено в «${p.name}»" else "Уже есть в «${p.name}»"
                                )
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                trackCountText(p.trackIds.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Диалог с одним полем для названия плейлиста. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                placeholder = { Text("Название") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
