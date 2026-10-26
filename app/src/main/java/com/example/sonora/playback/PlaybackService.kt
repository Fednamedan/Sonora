package com.example.sonora.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.sonora.data.Catalog
import com.example.sonora.data.Settings

/**
 * Фоновый сервис воспроизведения: уведомление с кнопками, экран блокировки, наушники, Bluetooth.
 *
 * Адреса вида sonora://<id> превращаются в реальную ссылку на поток прямо перед загрузкой,
 * поэтому очередь не «протухает», сколько бы она ни лежала. Скачанные треки играют по file://.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val api = Catalog(Settings(this))

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Sonora/1.0")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, http),
            ResolvingDataSource.Resolver { spec -> resolve(api, spec) },
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        session = MediaSession.Builder(this, player).build()
    }

    private fun resolve(api: Catalog, spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != SCHEME) return spec
        val id = uri.schemeSpecificPart.removePrefix("//")
        return spec.withUri(Uri.parse(api.streamUrl(id)))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val SCHEME = "sonora"
    }
}
