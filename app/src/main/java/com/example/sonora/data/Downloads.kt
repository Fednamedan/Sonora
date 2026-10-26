package com.example.sonora.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Загрузки для офлайна: файл сохраняется в приватную папку приложения и дальше играет без интернета.
 * Вызывать методы нужно из главного потока.
 */
class Downloads(
    context: Context,
    private val api: Catalog,
    private val store: Store,
    private val scope: CoroutineScope,
    private val messages: MutableSharedFlow<String>,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val jobs = HashMap<String, Job>()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Прогресс активных загрузок: id трека -> 0..1. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    init {
        // Если файл удалили вручную (очистка данных, файловый менеджер), убираем запись о загрузке
        store.retainDownloads { id -> file(id).exists() }
        dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
    }

    fun file(trackId: String): File = File(dir, "${safe(trackId)}.mp3")

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun isDownloaded(trackId: String): Boolean =
        trackId in store.state.value.downloadSet && file(trackId).exists()

    fun download(track: Track) {
        if (jobs.containsKey(track.id) || isDownloaded(track.id)) return
        if (!api.canDownload(track.id)) {
            messages.tryEmit("Этот трек скачать нельзя: он уже на телефоне или источник запрещает загрузку")
            return
        }
        jobs[track.id] = scope.launch {
            setProgress(track.id, 0f)
            try {
                withContext(Dispatchers.IO) { fetch(track) }
                store.markDownloaded(track)
                messages.tryEmit("Скачано: ${track.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.tryEmit("Не удалось скачать: ${track.title}")
            } finally {
                jobs.remove(track.id)
                _progress.update { it - track.id }
            }
        }
    }

    fun remove(track: Track) {
        jobs.remove(track.id)?.cancel()
        file(track.id).delete()
        File(dir, "${safe(track.id)}.part").delete()
        store.unmarkDownloaded(track.id)
    }

    // ---------- внутреннее ----------

    private fun setProgress(id: String, value: Float) {
        _progress.update { it + (id to value) }
    }

    private suspend fun fetch(track: Track) {
        val target = file(track.id)
        val part = File(dir, "${safe(track.id)}.part")
        val conn = URL(api.streamUrl(track.id)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Sonora/1.0")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")

            val total = conn.contentLengthLong
            var done = 0L
            val buffer = ByteArray(32 * 1024)
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) setProgress(track.id, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (done == 0L) throw IOException("empty file")
            if (!part.renameTo(target)) throw IOException("rename failed")
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
