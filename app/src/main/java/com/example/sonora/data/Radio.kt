package com.example.sonora.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/** С чего строить радио. */
sealed interface RadioSeed {
    /** «Моя волна»: подбор по лайкам и истории. */
    data object Wave : RadioSeed

    /** Радио жанра. */
    data class OfGenre(val genre: String) : RadioSeed

    /** Радио по треку: похожие по жанру, тегам и исполнителю. */
    data class OfTrack(val track: Track) : RadioSeed
}

/**
 * Рекомендации. У Audius нет готового «умного» радио, поэтому подбор делаем сами:
 * считаем профиль вкусов (жанры и теги из лайков и истории) и набираем свежие треки
 * из популярного по этим жанрам и тегам, не повторяя уже игравшее.
 */
class Radio(private val api: Catalog, private val store: Store) {

    private class Request(val run: suspend () -> List<Track>)

    suspend fun nextBatch(seed: RadioSeed, exclude: Set<String>, size: Int = 15): List<Track> {
        val requests = when (seed) {
            is RadioSeed.Wave -> waveRequests()
            is RadioSeed.OfGenre -> genreRequests(seed.genre)
            is RadioSeed.OfTrack -> trackRequests(seed.track)
        }

        var firstError: Exception? = null
        val results: List<List<Track>> = coroutineScope {
            requests.map { r ->
                async {
                    try {
                        r.run()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                        emptyList<Track>()
                    }
                }
            }.awaitAll()
        }

        val unique = LinkedHashMap<String, Track>()
        for (list in results) for (t in list) {
            if (t.id in exclude) continue
            if (t.durationSec != 0 && t.durationSec !in MIN_SEC..MAX_SEC) continue // отсекаем джинглы и многочасовые миксы
            unique.putIfAbsent(t.id, t)
        }
        if (unique.isEmpty()) {
            firstError?.let { throw it }
            return emptyList()
        }
        return unique.values.shuffled().take(size)
    }

    // ---------- наборы запросов ----------

    private fun waveRequests(): List<Request> {
        val taste = tasteProfile()
        val genres = if (taste.genres.isEmpty()) {
            Genres.coldStart.shuffled().take(3)
        } else {
            // Основные вкусы + один случайный жанр для разнообразия
            val picked = weightedPick(taste.genres, 2)
            val explore = Genres.coldStart.filter { it !in picked }.randomOrNull()
            listOfNotNull(*picked.toTypedArray(), explore)
        }
        val out = ArrayList<Request>()
        genres.forEach { g -> out.add(Request { api.trending(g, randomTime(), 25, Random.nextInt(0, 30)) }) }
        weightedPick(taste.tags, 2).forEach { t -> out.add(Request { api.search(t, 25, Random.nextInt(0, 20)) }) }
        return out
    }

    private fun genreRequests(genre: String): List<Request> = listOf(
        Request { api.trending(genre, randomTime(), 30, Random.nextInt(0, 40)) },
        Request { api.trending(genre, randomTime(), 30, Random.nextInt(0, 40)) },
        Request { api.search(genre, 20, Random.nextInt(0, 20)) },
    )

    private fun trackRequests(track: Track): List<Request> {
        val out = ArrayList<Request>()
        if (track.genre.isNotEmpty()) {
            out.add(Request { api.trending(track.genre, randomTime(), 25, Random.nextInt(0, 30)) })
        }
        track.tags.take(3).forEach { t -> out.add(Request { api.search(t, 20, Random.nextInt(0, 15)) }) }
        if (track.artist.isNotEmpty()) out.add(Request { api.search(track.artist, 10) })
        if (out.isEmpty()) out.add(Request { api.trending(null, "week", 30, Random.nextInt(0, 30)) })
        return out
    }

    // ---------- вкусы ----------

    private class Taste(val genres: Map<String, Double>, val tags: Map<String, Double>)

    private fun tasteProfile(): Taste {
        val s = store.state.value
        val genres = HashMap<String, Double>()
        val tags = HashMap<String, Double>()

        fun add(t: Track, weight: Double) {
            if (t.genre.isNotEmpty()) genres.merge(t.genre, weight, Double::plus)
            t.tags.forEach { tags.merge(it, weight, Double::plus) }
        }
        s.favoriteTracks().forEach { add(it, 3.0) }
        s.historyTracks().take(100).forEach { add(it, 1.0) }
        return Taste(genres, tags)
    }

    /** Случайный выбор без повторов; чем больше вес, тем вероятнее. */
    private fun weightedPick(weights: Map<String, Double>, count: Int): List<String> {
        val pool = weights.toMutableMap()
        val result = ArrayList<String>()
        repeat(count) {
            if (pool.isEmpty()) return result
            val total = pool.values.sum()
            var r = Random.nextDouble() * total
            var chosen = pool.keys.first()
            for ((k, w) in pool) {
                r -= w
                if (r <= 0) {
                    chosen = k
                    break
                }
            }
            result.add(chosen)
            pool.remove(chosen)
        }
        return result
    }

    private fun randomTime(): String = TIMES[Random.nextInt(TIMES.size)]

    private companion object {
        const val MIN_SEC = 60
        const val MAX_SEC = 12 * 60
        val TIMES = listOf("week", "month", "year")
    }
}
