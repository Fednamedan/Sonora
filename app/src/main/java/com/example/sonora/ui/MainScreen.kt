package com.example.sonora.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.sonora.data.Track
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val c = LocalContainer.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTarget by remember { mutableStateOf<Track?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val hasTrack by remember(c) {
        c.player.state.map { it.current != null }.distinctUntilChanged()
    }.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        c.messages.collect { message ->
            // новое сообщение заменяет старое, а не встаёт в очередь
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message) }
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    CompositionLocalProvider(LocalOpenTrackMenu provides { track -> menuTrack = track }) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).aurora()) {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = hasTrack,
                            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
                            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
                        ) {
                            MiniPlayer(onOpen = { playerOpen = true })
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = {
                                    Icon(
                                        if (tab == 0) Icons.Rounded.Home else Icons.Outlined.Home,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Главная") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = {
                                    Icon(
                                        if (tab == 1) Icons.Rounded.Search else Icons.Outlined.Search,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Поиск") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = {
                                    Icon(
                                        if (tab == 2) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Библиотека") },
                            )
                        }
                    }
                },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(240)) + slideInHorizontally { w -> w / 14 * dir }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally { w -> -w / 14 * dir })
                        },
                        label = "tabs",
                    ) { t ->
                        when (t) {
                            0 -> HomeScreen()
                            1 -> SearchScreen()
                            else -> LibraryScreen()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically(tween(380)) { it } + fadeIn(tween(380)),
                exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300)),
            ) {
                PlayerScreen(
                    onClose = { playerOpen = false },
                    onAddToPlaylist = { playlistTarget = it },
                )
            }
        }

        menuTrack?.let { track ->
            TrackMenuSheet(
                track = track,
                onDismiss = { menuTrack = null },
                onAddToPlaylist = { playlistTarget = it },
            )
        }
        playlistTarget?.let { track ->
            AddToPlaylistDialog(track = track, onDismiss = { playlistTarget = null })
        }
    }
}
