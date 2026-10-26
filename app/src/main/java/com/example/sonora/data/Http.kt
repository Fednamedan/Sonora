package com.example.sonora.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Простой блокирующий GET. Вызывать только с фонового потока. */
internal fun httpGetText(url: String, connectMs: Int = 6_000, readMs: Int = 15_000): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Sonora/1.0")
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
