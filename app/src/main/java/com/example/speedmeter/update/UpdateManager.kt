package com.example.speedmeter.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** [progress] is 0..1, or null when the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

/** Process-wide update state for the UI; the background worker uses [UpdateInstaller] directly. */
object UpdateManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Set by MainActivity; decides whether the install prompt opens directly or via a notification. */
    @Volatile
    var activityVisible = false

    private val busy: Boolean
        get() = _state.value.let {
            it is UpdateState.Checking || it is UpdateState.Downloading || it is UpdateState.Installing
        }

    fun check() {
        if (busy) return
        scope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { UpdateInstaller.fetchLatest() }.fold(
                onSuccess = { if (it.isNewer) UpdateState.Available(it) else UpdateState.UpToDate },
                onFailure = { UpdateState.Failed("Couldn't check for updates. Try again later.", null) },
            )
        }
    }

    fun install(context: Context, info: UpdateInfo) {
        if (busy) return
        val appContext = context.applicationContext
        scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            runCatching {
                val apk = UpdateInstaller.download(appContext, info) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
                _state.value = UpdateState.Installing(info)
                UpdateInstaller.install(appContext, apk)
            }.onFailure {
                _state.value = UpdateState.Failed("Download failed. Check your connection and try again.", info)
            }
        }
    }

    internal fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message, currentInfo())
    }

    internal fun onInstallCancelled() {
        currentInfo()?.let { _state.value = UpdateState.Available(it) }
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    }
}
