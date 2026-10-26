package com.example.sonora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Internet Archive: огромный открытый архив (нетлейблы, свободная музыка). Ключ не нужен.
 * Каждый «трек» здесь — релиз целиком; играет его первый mp3-файл. Длительность заранее неизвестна.
 */
class ArchiveApi {

    suspend fun trending(genre: String?, limit: Int, offset: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val extra = genre?.let { "subject:\"${esc(it)}\"" }
            parse(searchRaw(extra, limit, offset, broad = false))
                .ifEmpty { parse(searchRaw(extra, limit, offset, broad = true)) }
        }

    suspend fun search(query: String, limit: Int, offset: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val q = esc(query)
            val extra = "(title:\"$q\" OR creator:\"$q\" OR subject:\"$q\")"
            parse(searchRaw(extra, limit, offset, broad = false))
                .ifEmpty { parse(searchRaw(extra, limit, offset, broad = true)) }
        }

    /** Блокирующая: ссылка на первый mp3-файл релиза. */
    fun streamUrl(identifier: String): String {
        val files = JSONObject(httpGetText("https://archive.org/metadata/${URLEncoder.encode(identifier, "UTF-8")}"))
            .optJSONArray("files") ?: throw IOException("Archive: нет файлов")
        var best: String? = null
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val name = f.str("name") ?: continue
            val format = f.str("format") ?: ""
            if (!format.contains("MP3", ignoreCase = true)) continue
            if (best == null || name < best) best = name
        }
        val name = best ?: throw IOException("Archive: нет mp3")
        val path = name.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "https://archive.org/download/${URLEncoder.encode(identifier, "UTF-8")}/$path"
    }

    /** [broad] — запасной запрос без привязки к коллекциям, если по основному ничего не нашлось. */
    private fun searchRaw(extra: String?, limit: Int, offset: Int, broad: Boolean): String {
        val base = if (broad) "mediatype:audio AND format:MP3" else "mediatype:audio AND collection:(netlabels OR audio_music)"
        val q = if (extra == null) base else "$base AND $extra"
        val page = offset / limit.coerceAtLeast(1) + 1
        val fields = listOf("identifier", "title", "creator", "subject")
            .joinToString("&") { "fl%5B%5D=$it" }
        val url = "https://archive.org/advancedsearch.php?q=${URLEncoder.encode(q, "UTF-8")}&$fields" +
            "&sort%5B%5D=downloads+desc&rows=$limit&page=$page&output=json"
        return httpGetText(url)
    }

    private fun parse(json: String): List<Track> {
        val docs: JSONArray = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val out = ArrayList<Track>(docs.length())
        for (i in 0 until docs.length()) {
            val o = docs.optJSONObject(i) ?: continue
            val id = o.str("identifier") ?: continue
            val title = o.str("title") ?: continue
            val tags = strings(o.opt("subject")).map { it.lowercase() }.take(8)
            out.add(
                Track(
                    id = "ia:$id",
                    title = title,
                    artist = strings(o.opt("creator")).firstOrNull() ?: "",
                    durationSec = 0,
                    genre = tags.firstOrNull() ?: "",
                    tags = tags,
                    artworkUrl = "https://archive.org/services/img/$id",
                    permalink = "https://archive.org/details/$id",
                )
            )
        }
        return out
    }

    private fun strings(v: Any?): List<String> = when (v) {
        is String -> listOf(v)
        is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it)?.takeIf { s -> s.isNotBlank() } }
        else -> emptyList()
    }

    private fun esc(s: String) = s.replace("\\", " ").replace("\"", " ")
}
