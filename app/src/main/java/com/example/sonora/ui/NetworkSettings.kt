package com.example.sonora.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Сеть и источники: прокси для регионов, где сервисы блокируются, и ключ Jamendo. */
@Composable
fun NetworkSettingsDialog(onDismiss: () -> Unit) {
    val settings = LocalContainer.current.settings
    var proxy by remember { mutableStateOf(settings.proxy) }
    var key by remember { mutableStateOf(settings.jamendoKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сеть и источники") },
        text = {
            Column {
                Text(
                    "Если музыка не загружается, сервисы могут быть заблокированы. Самый надёжный способ: включить VPN на телефоне. " +
                        "Либо укажите свой прокси.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = proxy,
                    onValueChange = { proxy = it },
                    label = { Text("Прокси (host:port или socks5://host:port)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Ключ Jamendo (Client ID)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Бесплатный ключ: devportal.jamendo.com. Jamendo обычно доступен без VPN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.proxy = proxy
                settings.jamendoKey = key
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
