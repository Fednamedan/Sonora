package com.example.sonora.data

import org.json.JSONArray
import org.json.JSONObject

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val genre: String,
    val tags: List<String>,
    val artworkUrl: String?,
    val permalink: String?,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
)

data class HistoryEntry(
    val trackId: String,
    val playedAt: Long,
)

/** Всё, что пользователь накопил: лайки, плейлисты, история, загрузки. */
data class LibraryState(
    val tracks: Map<String, Track> = emptyMap(),
    val favorites: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val downloads: List<String> = emptyList(),
) {
    val favoriteSet: Set<String> by lazy(LazyThreadSafetyMode.NONE) { favorites.toSet() }
    val downloadSet: Set<String> by lazy(LazyThreadSafetyMode.NONE) { downloads.toSet() }

    fun favoriteTracks(): List<Track> = favorites.mapNotNull { tracks[it] }
    fun downloadedTracks(): List<Track> = downloads.mapNotNull { tracks[it] }
    fun historyTracks(): List<Track> = history.mapNotNull { tracks[it.trackId] }
    fun playlistTracks(p: Playlist): List<Track> = p.trackIds.mapNotNull { tracks[it] }
}

// ---------- JSON ----------

/** Строка из JSON или null, если поля нет, оно null или пустое. */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun Track.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("duration", durationSec)
    .put("genre", genre)
    .put("tags", JSONArray(tags))
    .put("artwork", artworkUrl ?: JSONObject.NULL)
    .put("permalink", permalink ?: JSONObject.NULL)

internal fun trackFromJson(o: JSONObject): Track? {
    val id = o.str("id") ?: return null
    val tagsArray = o.optJSONArray("tags")
    val tags = if (tagsArray == null) {
        emptyList()
    } else {
        (0 until tagsArray.length()).mapNotNull { i -> tagsArray.optString(i).takeIf { it.isNotBlank() } }
    }
    return Track(
        id = id,
        title = o.str("title") ?: "Без названия",
        artist = o.str("artist") ?: "",
        durationSec = o.optInt("duration", 0),
        genre = o.str("genre") ?: "",
        tags = tags,
        artworkUrl = o.str("artwork"),
        permalink = o.str("permalink"),
    )
}
