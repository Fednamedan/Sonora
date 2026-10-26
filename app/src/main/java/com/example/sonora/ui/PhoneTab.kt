package com.example.sonora.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sonora.data.LocalMusic
import com.example.sonora.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Вкладка «На телефоне»: музыка, которая уже есть на устройстве. */
@Composable
fun PhoneTab(ui: TrackUi) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Музыка с телефона")
            Text(
                "Разрешите доступ, и скачанные на телефон треки появятся здесь и будут играть без интернета.",
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = { launcher.launch(permission) }) { Text("Разрешить доступ") }
        }
        return
    }

    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(Unit) {
        tracks = try {
            withContext(Dispatchers.IO) { LocalMusic.scan(context) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    TrackTab(
        tracks = tracks ?: emptyList(),
        ui = ui,
        emptyTitle = if (tracks == null) "Ищу музыку…" else "На телефоне ничего не нашлось",
        emptyHint = "Положите mp3 или другие аудиофайлы в память телефона (папка Music или Загрузки)",
    )
}
