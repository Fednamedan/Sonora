package com.example.sonora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Клиент открытого API Audius (https://api.audius.co/v1). Ключ не нужен, достаточно app_name.
 */
class AudiusApi {

    /** Краткий итог последнего запроса. Показывается на экране, если каталог пуст или недоступен. */
    @Volatile
    var lastInfo: String = ""
        private set

    /** Популярное; [time] — week | month | year | allTime. */
    suspend fun trending(
        genre: String? = null,
        time: String = "week",
        limit: Int = 30,
        offset: Int = 0,
    ): List<Track> = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "time" to time,
            "limit" to limit.toString(),
            "offset" to offset.toString(),
        )
        if (genre != null) params["genre"] = genre
        parseTracks(get("/tracks/trending", params))
    }

    suspend fun search(query: String, limit: Int = 30, offset: Int = 0): List<Track> =
        withContext(Dispatchers.IO) {
            val params = linkedMapOf(
                "query" to query,
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            )
            parseTracks(get("/tracks/search", params))
        }

    /**
     * Прямая ссылка на аудиопоток. Блокирующая: вызывается из загрузчика плеера и менеджера загрузок.
     * Ссылки подписаны и со временем протухают, поэтому получаем их непосредственно перед воспроизведением.
     */
    fun streamUrl(id: String): String {
        try {
            val root = JSONObject(get("/tracks/$id", emptyMap()))
            val data = root.opt("data")
            val obj = when (data) {
                is JSONObject -> data
                is JSONArray -> data.optJSONObject(0)
                else -> null
            }
            val url = obj?.optJSONObject("stream")?.str("url")
            if (url != null) return url
        } catch (e: IOException) {
            // пробуем запасной путь ниже
        } catch (e: org.json.JSONException) {
            // то же самое
        }
        // Запасной вариант: эндпоинт сам перенаправит на актуальный адрес
        return "$BASE/tracks/$id/stream?app_name=$APP_NAME"
    }

    // ---------- внутреннее ----------

    private fun get(path: String, params: Map<String, String>): String {
        val query = (params + ("app_name" to APP_NAME)).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = URL("$BASE$path?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Sonora/1.0")
            val code = conn.responseCode
            if (code !in 200..299) {
                lastInfo = "HTTP $code"
                throw IOException("HTTP $code")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            lastInfo = "HTTP $code, ${text.length} симв."
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTracks(json: String): List<Track> {
        val root = JSONObject(json)
        val arr: JSONArray = when (val data = root.opt("data")) {
            is JSONArray -> data
            is JSONObject -> JSONArray().put(data)
            else -> return emptyList()
        }
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseTrack(o)?.let { out.add(it) }
        }
        lastInfo += ", элементов ${arr.length()}, принято ${out.size}"
        if (out.isEmpty()) lastInfo += ", начало ответа: ${json.take(200)}"
        return out
    }

    private fun parseTrack(o: JSONObject): Track? {
        val streamable = o.optBoolean("is_streamable", false) ||
            o.optJSONObject("stream")?.str("url") != null
        if (!streamable) return null
        if (o.optBoolean("is_delete", false) || o.optBoolean("is_unlisted", false)) return null
        val id = o.str("id") ?: return null
        val title = o.str("title") ?: return null
        val artist = o.optJSONObject("user")?.str("name") ?: ""
        val art = o.optJSONObject("artwork")
        val artwork = art?.str("480x480") ?: art?.str("1000x1000") ?: art?.str("150x150")
        val tags = o.str("tags")
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.take(8)
            ?: emptyList()
        return Track(
            id = id,
            title = title,
            artist = artist,
            durationSec = o.optInt("duration", 0),
            genre = o.str("genre") ?: "",
            tags = tags,
            artworkUrl = artwork,
            permalink = o.str("permalink")?.let { "https://audius.co$it" },
        )
    }

    private companion object {
        const val BASE = "https://api.audius.co/v1"
        const val APP_NAME = "Sonora"
    }
}
