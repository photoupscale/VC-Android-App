package com.voicechanger.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicechanger.app.audio.AudioDecoder
import com.voicechanger.app.audio.DecodedAudio
import com.voicechanger.app.audio.LiveMonitor
import com.voicechanger.app.audio.SampleRecorder
import com.voicechanger.app.engine.NativeBridge
import com.voicechanger.app.engine.VoiceMatcher
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoicePresets
import com.voicechanger.app.engine.VoiceProfile
import com.voicechanger.app.hook.ModuleStatus
import com.voicechanger.app.settings.MyVoice
import com.voicechanger.app.settings.SettingsRepository
import com.voicechanger.app.settings.VcSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What is being recorded for the "add voice from sample" flow. */
enum class SampleTarget { Own, Target }

data class AddVoiceState(
    val open: Boolean = false,
    val recording: SampleTarget? = null,
    val recordSeconds: Float = 0f,
    val busy: Boolean = false,
    val target: VoiceProfile? = null,
    val targetLabel: String? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    val settings: StateFlow<VcSettings> = repo.state

    val moduleActive: Boolean = ModuleStatus.isActive()
    val settingsShared: Boolean = repo.sharedWithHook

    private val monitor = LiveMonitor()
    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val sampler = SampleRecorder()
    private val _addVoice = MutableStateFlow(AddVoiceState())
    val addVoice: StateFlow<AddVoiceState> = _addVoice.asStateFlow()

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

    fun openAddVoice(open: Boolean) {
        if (!open && sampler.isRecording) sampler.stop()
        _addVoice.value = AddVoiceState(open = open)
    }

    fun toggleRecording(what: SampleTarget) {
        if (sampler.isRecording) {
            finishRecording()
            return
        }
        if (monitor.isRunning) toggleMonitor()
        sampler.start()
        _addVoice.update { it.copy(recording = what, recordSeconds = 0f, error = null) }
        viewModelScope.launch {
            while (sampler.isRecording) {
                _addVoice.update { it.copy(recordSeconds = sampler.seconds) }
                delay(200)
            }
            finishRecording()
        }
    }

    private fun finishRecording() {
        val what = _addVoice.value.recording ?: return
        _addVoice.update { it.copy(recording = null) }
        val audio = sampler.stop()
        analyze(what, "Recorded sample") { audio }
    }

    fun loadTargetFile(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio file"
        analyze(SampleTarget.Target, name) { AudioDecoder.decodeToMono(getApplication(), uri) }
    }

    private fun analyze(what: SampleTarget, label: String, load: () -> DecodedAudio) {
        _addVoice.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { load().let { VoiceMatcher.analyze(it.pcm, it.length, it.sampleRate) } }
            }
            val profile = result.getOrNull()
            when {
                profile == null -> _addVoice.update {
                    it.copy(busy = false, error = "Could not read that file: ${result.exceptionOrNull()?.message ?: "unknown error"}")
                }
                !profile.isValid -> _addVoice.update {
                    it.copy(busy = false, error = "Not enough clear speech was found. Use a recording with at least 3 seconds of one person talking, without music.")
                }
                what == SampleTarget.Own -> {
                    update { it.copy(ownProfile = profile.values) }
                    _addVoice.update { it.copy(busy = false) }
                }
                else -> _addVoice.update { it.copy(busy = false, target = profile, targetLabel = label) }
            }
        }
    }

    fun saveVoice(name: String) {
        val own = settings.value.ownProfile?.let(::VoiceProfile) ?: return
        val target = _addVoice.value.target ?: return
        val voice = MyVoice(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "My voice" }, VoiceMatcher.match(own, target))
        update { it.copy(myVoices = it.myVoices + voice, presetId = voice.presetId) }
        _addVoice.value = AddVoiceState()
    }

    fun deleteMyVoice(voice: MyVoice) = update { s ->
        s.copy(
            myVoices = s.myVoices - voice,
            presetId = if (s.presetId == voice.presetId) VcSettings().presetId else s.presetId,
        )
    }

    override fun onCleared() {
        monitor.stop()
        if (sampler.isRecording) sampler.stop()
    }
}
