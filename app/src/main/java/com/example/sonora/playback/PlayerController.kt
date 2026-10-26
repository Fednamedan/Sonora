package com.example.sonora.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.sonora.data.Downloads
import com.example.sonora.data.Radio
import com.example.sonora.data.RadioSeed
import com.example.sonora.data.Store
import com.example.sonora.data.Track
import com.example.sonora.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val radio: RadioSeed? = null,
    val radioLoading: Boolean = false,
    val sleepEndsAt: Long? = null,
)

/**
 * Единая точка управления музыкой для интерфейса. Все методы вызываются из главного потока.
 * Сам плеер живёт в [PlaybackService]; здесь мы держим зеркало очереди и логику радио.
 */
class PlayerController(
    private val context: Context,
    private val store: Store,
    private val downloads: Downloads,
    private val radio: Radio,
    private val messages: MutableSharedFlow<String>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private var queue: List<Track> = emptyList()
    private var radioSeed: RadioSeed? = null
    private var radioJob: Job? = null
    private var sleepJob: Job? = null
    private var recordedId: String? = null
    private var errorStreak = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.isPlaying) errorStreak = 0
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordedId = null
            maybeExtendRadio()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            errorStreak++
            // Если подряд упали все треки очереди (например, нет интернета), не крутимся по кругу
            if (errorStreak >= queue.size.coerceAtLeast(1) || !c.hasNextMediaItem()) {
                if (errorStreak == queue.size.coerceAtLeast(1) || !c.hasNextMediaItem()) {
                    messages.tryEmit("Не удалось воспроизвести трек. Проверьте подключение к интернету")
                }
                return
            }
            if (errorStreak == 1) messages.tryEmit("Трек не загрузился, перехожу к следующему")
            c.seekToNextMediaItem()
            c.prepare()
            c.play()
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.addListener(listener)
                controller = c
                restoreQueueFromSession(c)
                pending.forEach { it(c) }
                pending.clear()
                publish()
            } catch (e: Exception) {
                messages.tryEmit("Не удалось запустить плеер")
            }
        }, ContextCompat.getMainExecutor(context))

        // Позиция + учёт прослушиваний
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0L)) }
                    maybeRecord(c)
                }
            }
        }
    }

    // ---------- запуск воспроизведения ----------

    /** Играть список с позиции [index]; радио при этом выключается. */
    fun play(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        stopRadio()
        setQueue(tracks, index.coerceIn(0, tracks.lastIndex))
    }

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        stopRadio()
        setQueue(tracks.shuffled(), 0)
    }

    fun startRadio(seed: RadioSeed) {
        radioJob?.cancel()
        _state.update { it.copy(radioLoading = true) }
        radioJob = scope.launch {
            try {
                val batch = radio.nextBatch(seed, recentIds())
                if (batch.isEmpty()) {
                    messages.tryEmit("Не нашлось подходящих треков")
                    return@launch
                }
                val first = if (seed is RadioSeed.OfTrack) listOf(seed.track) + batch.filter { it.id != seed.track.id } else batch
                radioSeed = seed
                setQueue(first, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.tryEmit(e.userMessage())
            } finally {
                _state.update { it.copy(radioLoading = false) }
            }
        }
    }

    fun stopRadio() {
        radioJob?.cancel()
        radioJob = null
        radioSeed = null
        _state.update { it.copy(radio = null, radioLoading = false) }
    }

    // ---------- очередь ----------

    fun playNext(track: Track) {
        withController { c ->
            if (queue.isEmpty()) {
                setQueue(listOf(track), 0)
                return@withController
            }
            val at = (c.currentMediaItemIndex + 1).coerceAtMost(queue.size)
            queue = queue.toMutableList().also { it.add(at, track) }
            c.addMediaItem(at, item(track))
            publish()
            messages.tryEmit("Будет играть следующим")
        }
    }

    fun addToQueue(track: Track) {
        withController { c ->
            if (queue.isEmpty()) {
                setQueue(listOf(track), 0)
                return@withController
            }
            queue = queue + track
            c.addMediaItem(item(track))
            publish()
            messages.tryEmit("Добавлено в очередь")
        }
    }

    fun skipTo(index: Int) {
        withController { c ->
            if (index !in queue.indices) return@withController
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        withController { c ->
            if (index !in queue.indices || index == c.currentMediaItemIndex) return@withController
            queue = queue.toMutableList().also { it.removeAt(index) }
            c.removeMediaItem(index)
            publish()
        }
    }

    // ---------- управление ----------

    fun togglePlay() {
        withController { c ->
            if (c.isPlaying) {
                c.pause()
            } else {
                if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) {
        withController { c ->
            c.seekTo(ms)
            publish()
        }
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Таймер сна: через [minutes] минут музыка встанет на паузу. null — выключить. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) {
            _state.update { it.copy(sleepEndsAt = null) }
            return
        }
        val millis = minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = System.currentTimeMillis() + millis) }
        sleepJob = scope.launch {
            delay(millis)
            controller?.pause()
            _state.update { it.copy(sleepEndsAt = null) }
            messages.tryEmit("Таймер сна: музыка остановлена")
        }
    }

    // ---------- внутреннее ----------

    /**
     * Если приложение перезапустилось, а сервис всё это время играл, очередь в плеере жива,
     * а наше зеркало пустое. Восстанавливаем его из элементов сессии.
     */
    private fun restoreQueueFromSession(c: MediaController) {
        if (queue.isNotEmpty() || c.mediaItemCount == 0) return
        val known = store.state.value.tracks
        queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            known[item.mediaId] ?: Track(
                id = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "Без названия",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                durationSec = 0,
                genre = "",
                tags = emptyList(),
                artworkUrl = item.mediaMetadata.artworkUri?.toString(),
                permalink = null,
            )
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.add(block)
    }

    private fun setQueue(tracks: List<Track>, index: Int) {
        withController { c ->
            queue = tracks
            recordedId = null
            c.setMediaItems(tracks.map(::item), index, 0L)
            c.prepare()
            c.play()
            publish()
        }
    }

    private fun item(track: Track): MediaItem {
        val uri = if (track.id.startsWith("lc:")) {
            Uri.parse("content://media/external/audio/media/${track.id.removePrefix("lc:")}")
        } else if (downloads.isDownloaded(track.id)) {
            Uri.fromFile(downloads.file(track.id))
        } else {
            Uri.parse("${PlaybackService.SCHEME}://${Uri.encode(track.id)}")
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist.ifBlank { null })
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun publish() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: ((current?.durationSec ?: 0) * 1000L)
        _state.update {
            it.copy(
                current = current,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                queueIndex = if (current != null) index else -1,
                radio = radioSeed,
            )
        }
        maybeExtendRadio()
    }

    /** Засчитываем прослушивание после 20 секунд (или половины короткого трека). */
    private fun maybeRecord(c: MediaController) {
        val track = queue.getOrNull(c.currentMediaItemIndex) ?: return
        if (recordedId == track.id) return
        val threshold = minOf(20_000L, track.durationSec * 500L).coerceAtLeast(5_000L)
        if (c.currentPosition >= threshold) {
            recordedId = track.id
            store.recordPlay(track)
        }
    }

    private fun recentIds(): Set<String> {
        val ids = HashSet<String>()
        store.state.value.history.take(60).forEach { ids.add(it.trackId) }
        queue.forEach { ids.add(it.id) }
        return ids
    }

    /** Радио бесконечное: когда в очереди остаётся мало треков, подгружаем следующую порцию. */
    private fun maybeExtendRadio() {
        val seed = radioSeed ?: return
        val c = controller ?: return
        if (radioJob?.isActive == true) return
        if (queue.isEmpty() || c.currentMediaItemIndex < queue.size - 3) return

        radioJob = scope.launch {
            try {
                val batch = radio.nextBatch(seed, recentIds())
                if (batch.isNotEmpty() && radioSeed === seed) {
                    queue = queue + batch
                    c.addMediaItems(batch.map(::item))
                    publish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // тихо: попробуем при следующем переключении трека
            }
        }
    }
}
