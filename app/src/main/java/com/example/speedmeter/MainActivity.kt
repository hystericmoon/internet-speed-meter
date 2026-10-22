package com.example.speedmeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private var notificationsDenied by mutableStateOf(false)
    private var transparentFallback by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsDenied = !granted
            // Start regardless: the service still runs, it just can't show anything.
            SpeedMeterService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        transparentFallback = SpikeFlags.transparentIconFallback(this)

        setContent {
            SpeedMeterTheme {
                val state by SpeedMeterService.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    notificationsDenied = notificationsDenied,
                    transparentFallback = transparentFallback,
                    onStart = ::onStartClicked,
                    onStop = { SpeedMeterService.stop(this) },
                    onTransparentFallbackChange = {
                        transparentFallback = it
                        SpikeFlags.setTransparentIconFallback(this, it)
                    },
                    onOpenNotificationSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up a grant made from system settings while we were in the background.
        if (notificationsDenied && hasNotificationPermission()) notificationsDenied = false
    }

    private fun onStartClicked() {
        if (hasNotificationPermission()) {
            SpeedMeterService.start(this)
        } else {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun MainScreen(
    state: MeterState?,
    notificationsDenied: Boolean,
    transparentFallback: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTransparentFallbackChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Speed Meter", style = MaterialTheme.typography.headlineMedium)

            val running = state != null
            Text(
                text = when {
                    state == null -> "Stopped"
                    state.paused -> "Paused (screen off)"
                    state.hidden -> "Idle, hidden from status bar"
                    else -> "Running"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state != null) {
                Text(
                    "↓ ${SpeedFormat.full(state.downBytesPerSec)}\n↑ ${SpeedFormat.full(state.upBytesPerSec)}",
                    style = MaterialTheme.typography.displaySmall,
                )
            }

            Row {
                Button(onClick = onStart, enabled = !running) { Text("Start") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onStop, enabled = running) { Text("Stop") }
            }

            if (notificationsDenied) {
                Text(
                    "Notifications are off, so the speed can't appear in the status bar.",
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenNotificationSettings) { Text("Open notification settings") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = transparentFallback,
                        role = Role.Switch,
                        onValueChange = onTransparentFallbackChange,
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Transparent-icon fallback", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When idle, blank the icon instead of moving to the minimum-importance channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Row handles the toggle so the whole line is one touch target and one a11y node.
                Switch(checked = transparentFallback, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun SpeedMeterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
