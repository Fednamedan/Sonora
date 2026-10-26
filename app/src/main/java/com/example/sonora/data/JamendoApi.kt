package com.example.sonora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Jamendo: легальная музыка независимых артистов под лицензиями Creative Commons. Нужен бесплатный ключ. */
class JamendoApi(private val clientId: String) {

    suspend fun trending(genre: String?, time: String, limit: Int, offset: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val order = when (time) {
                "week" -> "popularity_week"
                "month" -> "popularity_month"
                else -> "popularity_total"
            }
            val params = linkedMapOf("order" to order, "limit" to limit.toString(), "offset" to offset.toString())
            if (genre != null) params["fuzzytags"] = genreTags(genre)
            parse(call(params))
        }

    suspend fun search(query: String, limit: Int, offset: Int): List<Track> =
        withContext(Dispatchers.IO) {
            parse(call(linkedMapOf("search" to query, "limit" to limit.toString(), "offset" to offset.toString())))
        }

    /** Блокирующая: прямая ссылка на mp3 для трека с «сырым» (без префикса) id. */
    fun streamUrl(rawId: String): String {
        val obj = JSONObject(call(linkedMapOf("id" to rawId, "limit" to "1")))
            .optJSONArray("results")?.optJSONObject(0)
        return obj?.str("audio") ?: throw java.io.IOException("Jamendo: нет ссылки на поток")
    }

    private fun call(params: Map<String, String>): String {
        val all = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
            "audioformat" to "mp32",
            "imagesize" to "500",
            "include" to "musicinfo",
        ) + params
        val query = all.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return httpGetText("https://api.jamendo.com/v3.0/tracks/?$query")
    }

    private fun parse(json: String): List<Track> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("id") ?: continue
            val title = o.str("name") ?: continue
            if (o.str("audio") == null) continue
            val genres = o.optJSONObject("musicinfo")?.optJSONObject("tags")?.optJSONArray("genres")
            val tags = ArrayList<String>()
            if (genres != null) for (k in 0 until genres.length()) genres.optString(k)?.takeIf { it.isNotBlank() }?.let { tags.add(it.lowercase()) }
            out.add(
                Track(
                    id = "jm:$id",
                    title = title,
                    artist = o.str("artist_name") ?: "",
                    durationSec = o.optInt("duration", 0),
                    genre = tags.firstOrNull() ?: "",
                    tags = tags.take(8),
                    artworkUrl = o.str("image"),
                    permalink = o.str("shareurl"),
                )
            )
        }
        return out
    }

    private fun genreTags(genre: String): String {
        val t = genre.lowercase().filter { it.isLetterOrDigit() }
        return when (t) {
            "hiphoprap" -> "hiphop+rap"
            "rbsoul" -> "rnb+soul"
            "drumbass" -> "drumnbass"
            "lofi" -> "lofi+chillout"
            "alternative" -> "alternative+indie"
            else -> t
        }
    }
}
