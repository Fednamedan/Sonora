package com.example.sonora.data

data class Genre(val api: String, val label: String)

/** Жанры Audius: [api] уходит в запрос, [label] показывается пользователю. */
object Genres {
    val featured: List<Genre> = listOf(
        Genre("Electronic", "Электронная"),
        Genre("Hip-Hop/Rap", "Хип-хоп / рэп"),
        Genre("Pop", "Поп"),
        Genre("Rock", "Рок"),
        Genre("Lo-Fi", "Lo-Fi"),
        Genre("House", "Хаус"),
        Genre("Techno", "Техно"),
        Genre("Ambient", "Эмбиент"),
        Genre("Drum & Bass", "Drum & Bass"),
        Genre("Trap", "Трэп"),
        Genre("R&B/Soul", "R&B / соул"),
        Genre("Alternative", "Альтернатива"),
        Genre("Metal", "Метал"),
        Genre("Jazz", "Джаз"),
        Genre("Classical", "Классика"),
        Genre("Folk", "Фолк"),
        Genre("Funk", "Фанк"),
        Genre("Reggae", "Регги"),
        Genre("Latin", "Латино"),
        Genre("Soundtrack", "Саундтреки"),
        Genre("Vaporwave", "Вейпорвейв"),
        Genre("Hyperpop", "Хайперпоп"),
        Genre("Dubstep", "Дабстеп"),
        Genre("Trance", "Транс"),
    )

    /** С чего начинать «Мою волну», пока о вкусах ничего не известно. */
    val coldStart: List<String> = listOf(
        "Electronic", "Hip-Hop/Rap", "Pop", "Rock", "Lo-Fi",
        "House", "Alternative", "R&B/Soul", "Ambient", "Jazz",
    )

    fun label(api: String): String = featured.firstOrNull { it.api == api }?.label ?: api
}
