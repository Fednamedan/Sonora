package com.example.sonora.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Состояние загрузки данных с сервера. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val message: String) : Load<Nothing>
}

/** Понятное человеку описание сетевой ошибки. */
fun Throwable.userMessage(): String {
    val base = when (this) {
        is UnknownHostException, is ConnectException -> "Нет связи с каталогом музыки"
        is SocketTimeoutException -> "Каталог долго не отвечает. Попробуйте ещё раз"
        else -> "Не удалось загрузить. Попробуйте ещё раз"
    }
    // Техническая строка помогает понять причину (сеть, блокировка, формат ответа)
    return "$base\n[${javaClass.simpleName}: ${message ?: "—"}]"
}

fun formatDuration(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
