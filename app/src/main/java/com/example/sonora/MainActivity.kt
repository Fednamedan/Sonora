package com.example.sonora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.sonora.ui.DefaultAccent
import com.example.sonora.ui.LocalContainer
import com.example.sonora.ui.MainScreen
import com.example.sonora.ui.SonoraTheme
import com.example.sonora.ui.rememberArtworkColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* без уведомления музыка всё равно играет */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as SonoraApp).container
        setContent {
            // Акцент всего приложения берётся из обложки играющего трека
            val artworkUrl by remember {
                container.player.state.map { it.current?.artworkUrl }.distinctUntilChanged()
            }.collectAsState(initial = null)
            val accentSeed = rememberArtworkColor(artworkUrl, DefaultAccent)

            SonoraTheme(accentSeed = accentSeed) {
                CompositionLocalProvider(LocalContainer provides container) {
                    MainScreen()
                }
            }
        }
    }
}
