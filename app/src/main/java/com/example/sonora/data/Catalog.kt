package com.example.sonora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException

/**
 * Единый каталог поверх нескольких источников. Все источники опрашиваются параллельно,
 * результаты перемешиваются; если один из них заблокирован или лежит, остальные продолжают работать.
 *
 * Префиксы id: без префикса — Audius (так хранились треки раньше), "jm:" — Jamendo, "ia:" — Internet Archive.
 */
class Catalog(private val settings: Settings) {
    private val audius = AudiusApi()
    /** Ключ берём из Config или из настроек приложения (⚙ на главной). */
    private fun jamendo(): JamendoApi? =
        Config.JAMENDO_CLIENT_ID.ifBlank { settings.jamendoKey }.takeIf { it.isNotBlank() }?.let { JamendoApi(it) }
    private val archive = ArchiveApi()

    private val status = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Состояние источников после последних запросов: показывается на экране при проблемах. */
    val lastInfo: String
        get() = listOf("Audius", "Jamendo", "Archive").joinToString("; ") { "$it: ${status[it] ?: "—"}" }

    suspend fun trending(
        genre: String? = null,
        time: String = "week",
        limit: Int = 30,
        offset: Int = 0,
    ): List<Track> = gather(limit) {
        val jm = jamendo()
        listOfNotNull(
            "Audius" to suspend { audius.trending(genre, time, limit, offset) },
            jm?.let { j -> "Jamendo" to suspend { j.trending(genre, time, limit, offset) } },
            "Archive" to suspend { archive.trending(genre, limit, offset) },
        )
    }

    suspend fun search(query: String, limit: Int = 30, offset: Int = 0): List<Track> = gather(limit) {
        val jm = jamendo()
        listOfNotNull(
            "Audius" to suspend { audius.search(query, limit, offset) },
            jm?.let { j -> "Jamendo" to suspend { j.search(query, limit, offset) } },
            "Archive" to suspend { archive.search(query, limit, offset) },
        )
    }

    /** Блокирующая: свежая ссылка на поток для любого трека. */
    fun streamUrl(id: String): String = when {
        id.startsWith("jm:") -> (jamendo() ?: throw IOException("Jamendo отключён: нужен ключ")).streamUrl(id.removePrefix("jm:"))
        id.startsWith("ia:") -> archive.streamUrl(id.removePrefix("ia:"))
        else -> audius.streamUrl(id)
    }

    /** Условия Jamendo не разрешают приложениям кэшировать и отдавать офлайн, поэтому его треки только онлайн. */
    fun canDownload(id: String): Boolean = !id.startsWith("jm:") && !id.startsWith("lc:")

    private suspend fun gather(
        limit: Int,
        sources: () -> List<Pair<String, suspend () -> List<Track>>>,
    ): List<Track> = coroutineScope {
        var firstError: Exception? = null
        val lists = sources().map { (name, call) ->
            async(Dispatchers.IO) {
                try {
                    val r = call()
                    status[name] = "ок, ${r.size}"
                    r
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status[name] = "${e.javaClass.simpleName}: ${e.message}"
                    if (firstError == null) firstError = e
                    emptyList<Track>()
                }
            }
        }.awaitAll()

        if (lists.all { it.isEmpty() }) {
            if (firstError != null) throw IOException("Ни один источник не ответил\n$lastInfo")
            return@coroutineScope emptyList()
        }
        // Чередуем источники, чтобы выдача была смешанной
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        var i = 0
        while (out.size < limit && lists.any { i < it.size }) {
            for (l in lists) {
                val t = l.getOrNull(i) ?: continue
                if (seen.add(t.id) && out.size < limit) out.add(t)
            }
            i++
        }
        out
    }
}
