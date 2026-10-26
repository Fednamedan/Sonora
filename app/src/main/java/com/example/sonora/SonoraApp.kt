package com.example.sonora

import android.app.Application
import android.content.Context
import com.example.sonora.data.Catalog
import com.example.sonora.data.Settings
import com.example.sonora.data.Downloads
import com.example.sonora.data.Radio
import com.example.sonora.data.Store
import com.example.sonora.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/** Все общие объекты приложения в одном месте. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Короткие сообщения пользователю (показываются в Snackbar). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val settings = Settings(context).also { it.applyProxy() }
    val api = Catalog(settings)
    val store = Store(context, scope)
    val downloads = Downloads(context, api, store, scope, messages)
    val radio = Radio(api, store)
    val player = PlayerController(context, store, downloads, radio, messages, scope)
}

class SonoraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
