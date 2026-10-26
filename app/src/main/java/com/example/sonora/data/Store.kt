package com.example.sonora.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Хранилище пользовательских данных: избранное, плейлисты, история, список загрузок.
 * Всё лежит в одном JSON-файле; запись идёт в фоне и не блокирует интерфейс.
 */
class Store(context: Context, scope: CoroutineScope) {

    private val file = File(context.filesDir, "library.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (request in saveRequests) {
                try {
                    write(_state.value)
                } catch (e: Exception) {
                    // не удалось записать сейчас — повторим при следующем изменении
                }
            }
        }
    }

    // ---------- избранное ----------

    fun toggleFavorite(track: Track) = mutate { s ->
        if (track.id in s.favoriteSet) {
            s.copy(favorites = s.favorites - track.id)
        } else {
            s.copy(favorites = listOf(track.id) + s.favorites, tracks = s.tracks + (track.id to track))
        }
    }

    // ---------- плейлисты ----------

    fun createPlaylist(name: String, first: Track? = null): String {
        val id = UUID.randomUUID().toString()
        mutate { s ->
            val playlist = Playlist(id, name.trim().ifEmpty { "Без названия" }, listOfNotNull(first?.id))
            s.copy(
                playlists = s.playlists + playlist,
                tracks = if (first != null) s.tracks + (first.id to first) else s.tracks,
            )
        }
        return id
    }

    fun renamePlaylist(id: String, name: String) = mutate { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun deletePlaylist(id: String) = mutate { s ->
        s.copy(playlists = s.playlists.filterNot { it.id == id })
    }

    /** @return false, если трек уже был в плейлисте. */
    fun addToPlaylist(playlistId: String, track: Track): Boolean {
        var added = false
        mutate { s ->
            added = false // лямбда может выполниться повторно при гонке обновлений
            s.copy(
                playlists = s.playlists.map { p ->
                    if (p.id == playlistId && track.id !in p.trackIds) {
                        added = true
                        p.copy(trackIds = p.trackIds + track.id)
                    } else p
                },
                tracks = s.tracks + (track.id to track),
            )
        }
        return added
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) = mutate { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
        })
    }

    // ---------- история ----------

    fun recordPlay(track: Track) = mutate { s ->
        val entry = HistoryEntry(track.id, System.currentTimeMillis())
        s.copy(
            history = (listOf(entry) + s.history.filter { it.trackId != track.id }).take(MAX_HISTORY),
            tracks = s.tracks + (track.id to track),
        )
    }

    fun clearHistory() = mutate { s -> s.copy(history = emptyList()) }

    // ---------- загрузки ----------

    fun markDownloaded(track: Track) = mutate { s ->
        if (track.id in s.downloadSet) s
        else s.copy(downloads = listOf(track.id) + s.downloads, tracks = s.tracks + (track.id to track))
    }

    fun unmarkDownloaded(trackId: String) = mutate { s ->
        s.copy(downloads = s.downloads - trackId)
    }

    /** Убирает из списка загрузки, файлов которых больше нет на диске. */
    fun retainDownloads(exists: (String) -> Boolean) = mutate { s ->
        val kept = s.downloads.filter(exists)
        if (kept.size == s.downloads.size) s else s.copy(downloads = kept)
    }

    // ---------- внутреннее ----------

    private fun mutate(transform: (LibraryState) -> LibraryState) {
        _state.update { prune(transform(it)) }
        saveRequests.trySend(Unit)
    }

    /** Выбрасываем описания треков, на которые больше никто не ссылается. */
    private fun prune(s: LibraryState): LibraryState {
        val used = HashSet<String>()
        used.addAll(s.favorites)
        used.addAll(s.downloads)
        s.playlists.forEach { used.addAll(it.trackIds) }
        s.history.forEach { used.add(it.trackId) }
        return if (s.tracks.keys.all { it in used }) s
        else s.copy(tracks = s.tracks.filterKeys { it in used })
    }

    private fun load(): LibraryState {
        if (!file.exists()) return LibraryState()
        return try {
            parse(file.readText())
        } catch (e: Exception) {
            LibraryState()
        }
    }

    private fun parse(text: String): LibraryState {
        val root = JSONObject(text)

        val tracks = LinkedHashMap<String, Track>()
        root.optJSONArray("tracks")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let(::trackFromJson)?.let { tracks[it.id] = it }
            }
        }

        fun strings(key: String): List<String> {
            val arr = root.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
        }

        val playlists = ArrayList<Playlist>()
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ids = o.optJSONArray("tracks")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } }
                } ?: emptyList()
                playlists.add(Playlist(o.optString("id"), o.optString("name"), ids))
            }
        }

        val history = ArrayList<HistoryEntry>()
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                history.add(HistoryEntry(o.optString("id"), o.optLong("at")))
            }
        }

        return LibraryState(
            tracks = tracks,
            favorites = strings("favorites"),
            playlists = playlists,
            history = history,
            downloads = strings("downloads"),
        )
    }

    private fun write(s: LibraryState) {
        val root = JSONObject()
        root.put("tracks", JSONArray().also { a -> s.tracks.values.forEach { a.put(it.toJson()) } })
        root.put("favorites", JSONArray(s.favorites))
        root.put("downloads", JSONArray(s.downloads))
        root.put("playlists", JSONArray().also { a ->
            s.playlists.forEach { p ->
                a.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("tracks", JSONArray(p.trackIds))
                )
            }
        })
        root.put("history", JSONArray().also { a ->
            s.history.forEach { h -> a.put(JSONObject().put("id", h.trackId).put("at", h.playedAt)) }
        })

        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    private companion object {
        const val MAX_HISTORY = 300
    }
}
