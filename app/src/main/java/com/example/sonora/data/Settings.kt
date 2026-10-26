package com.example.sonora.data

import android.content.Context
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** Пользовательские настройки сети и источников. Хранятся в SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var jamendoKey: String
        get() = prefs.getString("jamendo", "")?.trim().orEmpty()
        set(v) = prefs.edit().putString("jamendo", v.trim()).apply()

    /** Формат: host:port, http://host:port или socks5://host:port. Пусто — без прокси. */
    var proxy: String
        get() = prefs.getString("proxy", "")?.trim().orEmpty()
        set(v) {
            prefs.edit().putString("proxy", v.trim()).apply()
            applyProxy()
        }

    /** Подключает прокси для всех сетевых запросов приложения (каталог, плеер, обложки). */
    fun applyProxy() {
        val spec = proxy
        val parsed = parse(spec)
        ProxySelector.setDefault(
            if (parsed == null) original
            else object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> = listOf(parsed)
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
            }
        )
    }

    private fun parse(spec: String): Proxy? {
        if (spec.isBlank()) return null
        val socks = spec.startsWith("socks", ignoreCase = true)
        val hostPort = spec.substringAfter("://")
        val i = hostPort.lastIndexOf(':')
        if (i <= 0) return null
        val host = hostPort.substring(0, i)
        val port = hostPort.substring(i + 1).trim().toIntOrNull() ?: return null
        return Proxy(
            if (socks) Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved(host, port),
        )
    }

    private companion object {
        val original: ProxySelector? = ProxySelector.getDefault()
    }
}
