package com.voicechanger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.voicechanger.app.audio.LiveMonitor
import com.voicechanger.app.engine.NativeBridge
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoicePresets
import com.voicechanger.app.hook.ModuleStatus
import com.voicechanger.app.settings.SettingsRepository
import com.voicechanger.app.settings.VcSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    val settings: StateFlow<VcSettings> = repo.state

    val moduleActive: Boolean = ModuleStatus.isActive()
    val settingsShared: Boolean = repo.sharedWithHook

    private val monitor = LiveMonitor()
    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    init {
        NativeBridge.loadDefault()
    }

    fun update(change: (VcSettings) -> VcSettings) {
        repo.update(change)
        if (monitor.isRunning) monitor.setVoice(repo.state.value.voice())
    }

    fun selectPreset(id: String) = update { it.copy(presetId = id) }

    fun setCustom(params: VoiceParams) = update { it.copy(custom = params, presetId = VoicePresets.CUSTOM_ID) }

    fun toggleMonitor() {
        if (monitor.isRunning) monitor.stop() else monitor.start(settings.value.voice())
        _monitoring.value = monitor.isRunning
    }

    override fun onCleared() {
        monitor.stop()
    }
}
