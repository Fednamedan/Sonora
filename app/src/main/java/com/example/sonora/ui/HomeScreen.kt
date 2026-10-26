package com.example.sonora.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sonora.SonoraApp
import com.example.sonora.data.Genres
import com.example.sonora.data.RadioSeed
import com.example.sonora.data.Track
import com.example.sonora.util.Load
import com.example.sonora.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as SonoraApp).container.api

    private val _trending = MutableStateFlow<Load<List<Track>>>(Load.Loading)
    val trending: StateFlow<Load<List<Track>>> = _trending.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _trending.value = Load.Loading
            _trending.value = try {
                Load.Ok(api.trending(limit = 30, time = "week"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Fail(e.userMessage())
            }
        }
    }
}

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    var genre by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedContent(
        targetState = genre,
        transitionSpec = { pushPop(targetState != null) },
        label = "home",
    ) { g ->
        if (g != null) {
            BackHandler { genre = null }
            GenreScreen(g, onBack = { genre = null })
        } else {
            HomeContent(vm, onGenre = { genre = it })
        }
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

@Composable
private fun HomeContent(vm: HomeViewModel, onGenre: (String) -> Unit) {
    val c = LocalContainer.current
    val trending by vm.trending.collectAsState()
    val lib by c.store.state.collectAsState()
    val ui = rememberTrackUi()

    val history = remember(lib.history, lib.tracks) { lib.historyTracks().take(12) }
    val hasTaste = lib.favorites.isNotEmpty() || lib.history.isNotEmpty()
    val hello = remember { greeting() }

    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) NetworkSettingsDialog(onDismiss = { showSettings = false })
    val glow = MaterialTheme.colorScheme.primary
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.20f), Color.Transparent), endY = 1100f)),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(hello, Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { showSettings = true }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.Settings,
                        contentDescription = "Сеть и источники",
                    )
                }
            }
        }
        item { WaveCard(hasTaste) }

        item {
            LazyRow(
                Modifier.padding(top = 14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(MOODS) { i, (label, genre) ->
                    androidx.compose.material3.AssistChip(
                        onClick = { c.player.startRadio(RadioSeed.OfGenre(genre)) },
                        label = { Text(label) },
                        modifier = Modifier.staggeredAppear(i),
                    )
                }
            }
        }

        item { SectionTitle("Жанры") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Genres.featured) { g ->
                    GenreTile(g, Modifier.width(138.dp)) { onGenre(g.api) }
                }
            }
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("Недавно слушали") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(history) { index, t ->
                        HistoryCard(t) { c.player.play(history, index) }
                    }
                }
            }
        }

        item { SectionTitle("Популярное на этой неделе") }
        when (val t = trending) {
            is Load.Loading -> item { TrackSkeleton() }
            is Load.Fail -> item { ErrorBox(t.message + "\n\nЕсли в вашем регионе сервисы недоступны, откройте ⚙ вверху: там прокси и ключ Jamendo.", onRetry = vm::load) }
            is Load.Ok -> if (t.value.isEmpty()) {
                item {
                    ErrorBox(
                        "Каталог ответил, но треков в ответе нет\n[${c.api.lastInfo}]",
                        onRetry = vm::load,
                    )
                }
            } else {
                trackItems(t.value, ui, onPlay = { c.player.play(t.value, it) })
            }
        }
    }
}

/** Главная кнопка приложения: бесконечная волна по вашим вкусам. */
@Composable
private fun WaveCard(hasTaste: Boolean) {
    val c = LocalContainer.current
    val loading by remember(c) {
        c.player.state.map { it.radioLoading }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val waveActive by remember(c) {
        c.player.state.map { it.radio is RadioSeed.Wave }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val playing by remember(c) {
        c.player.state.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)

    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "wave")
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .drawBehind {
                // Два медленных световых пятна цвета акцента
                val d = drift.value
                val r = size.height * 1.15f
                val first = Offset(size.width * (0.72f + 0.2f * d), size.height * (0.15f + 0.35f * d))
                val second = Offset(size.width * (0.95f - 0.35f * d), size.height * (0.95f - 0.3f * d))
                val strong = if (dark) 0.50f else 0.38f
                drawRect(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = strong), Color.Transparent),
                        center = first,
                        radius = r,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = strong * 0.55f), Color.Transparent),
                        center = second,
                        radius = r * 0.8f,
                    )
                )
            }
            .padding(22.dp)
    ) {
        Column {
            Text("Моя волна", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasTaste) "Музыка по вашим лайкам и истории, без конца"
                else "Пока подбираем из популярного. Ставьте лайки, и волна подстроится",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { c.player.startRadio(RadioSeed.Wave) },
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (waveActive) "Обновить" else "Слушать")
                }
                if (waveActive) {
                    Spacer(Modifier.width(16.dp))
                    EqualizerBars(playing, scheme.primary, Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(124.dp)) {
        Artwork(
            track.artworkUrl,
            Modifier
                .size(124.dp)
                .bounceClick(onClick),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            track.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Популярное в жанре и радио жанра. */
@Composable
fun GenreScreen(genre: String, onBack: () -> Unit) {
    val c = LocalContainer.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load<List<Track>>>(Load.Loading, genre, attempt) {
        value = Load.Loading
        value = try {
            Load.Ok(c.api.trending(genre, "month", 40))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Fail(e.userMessage())
        }
    }
    val ui = rememberTrackUi()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DetailHeader(Genres.label(genre), onBack, subtitle = "Популярное за месяц") }
        when (val s = state) {
            is Load.Loading -> item { TrackSkeleton() }
            is Load.Fail -> item { ErrorBox(s.message, onRetry = { attempt++ }) }
            is Load.Ok -> {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { c.player.play(s.value, 0) }, enabled = s.value.isNotEmpty()) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Слушать")
                        }
                        OutlinedButton(onClick = { c.player.startRadio(RadioSeed.OfGenre(genre)) }) {
                            Icon(Icons.Rounded.Radio, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Радио жанра")
                        }
                    }
                }
                if (s.value.isEmpty()) {
                    item { EmptyState("Пока пусто", "В этом жанре ничего не нашлось") }
                } else {
                    trackItems(s.value, ui, onPlay = { c.player.play(s.value, it) })
                }
            }
        }
    }
}

/** Настроения для быстрого радио: название и жанр, из которого оно строится. */
private val MOODS = listOf(
    "Бодро" to "House",
    "Спокойно" to "Ambient",
    "Фокус" to "Lo-Fi",
    "Ночь" to "Jazz",
    "Энергия" to "Drum & Bass",
    "Грусть" to "Alternative",
    "Танцы" to "Electronic",
)
