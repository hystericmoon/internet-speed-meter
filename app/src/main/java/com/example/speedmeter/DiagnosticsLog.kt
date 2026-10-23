package com.example.speedmeter

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spike-only: a short log of what the meter and overlay did and why, shown in the app so
 * device results can be reported from the phone. Persisted, so it survives Android killing
 * the process (which is itself one of the things it's meant to reveal).
 */
object DiagnosticsLog {
    private const val MAX_EVENTS = 60
    private const val PREFS = "diagnostics"
    private const val KEY_LINES = "lines"
    private val time = SimpleDateFormat("MMM d HH:mm:ss", Locale.US)

    private var prefs: SharedPreferences? = null
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    /** Call once per process; without it (e.g. in tests) the log is in-memory only. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _events.value = p.getString(KEY_LINES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    @Synchronized
    fun log(message: String) {
        val line = "${time.format(Date())}  $message"
        _events.update { (it + line).takeLast(MAX_EVENTS) }
        prefs?.edit()?.putString(KEY_LINES, _events.value.joinToString("\n"))?.apply()
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        prefs?.edit()?.remove(KEY_LINES)?.apply()
    }
}
