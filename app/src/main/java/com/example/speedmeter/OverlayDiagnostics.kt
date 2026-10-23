package com.example.speedmeter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spike-only: a short in-memory log of what the overlay's full-screen detection saw, shown
 * in the app so device results can be reported without a computer or logcat.
 */
object OverlayDiagnostics {
    private const val MAX_EVENTS = 12
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    fun log(message: String) {
        val line = "${time.format(Date())}  $message"
        _events.update { (it + line).takeLast(MAX_EVENTS) }
    }
}
