package com.voicechanger.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = openPrefs(context)
    private val _state = MutableStateFlow(Prefs.read(prefs))
    val state: StateFlow<VcSettings> = _state.asStateFlow()

    /** False when LSPosed is not hooking this app, so call apps cannot read the settings yet. */
    val sharedWithHook: Boolean = worldReadable

    @Synchronized
    fun update(change: (VcSettings) -> VcSettings) {
        val next = change(_state.value).copy(revision = System.currentTimeMillis())
        Prefs.write(prefs, next)
        _state.value = next
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        private var worldReadable = false

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        @SuppressLint("WorldReadableFiles")
        @Suppress("DEPRECATION")
        private fun openPrefs(context: Context): SharedPreferences =
            try {
                // LSPosed (with xposedsharedprefs) allows this and exposes the file to hooked apps.
                context.getSharedPreferences(Prefs.FILE, Context.MODE_WORLD_READABLE).also { worldReadable = true }
            } catch (e: SecurityException) {
                // Module not active: Android forbids world-readable files. Keep working locally.
                context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            }
    }
}
