package com.voicechanger.app.hook

import android.media.AudioRecord
import android.os.Build
import android.os.Process
import com.voicechanger.app.BuildConfig
import com.voicechanger.app.engine.NativeBridge
import com.voicechanger.app.engine.VoiceProcessor
import com.voicechanger.app.settings.Prefs
import com.voicechanger.app.settings.VcSettings
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Per-process state inside a hooked call app: settings, native library, one converter per AudioRecord. */
class MicSession(private val packageName: String, private val modulePath: String?) {
    private val processors = Collections.synchronizedMap(WeakHashMap<AudioRecord, VoiceProcessor>())
    private val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, Prefs.FILE)
    private val refresher = Executors.newSingleThreadExecutor { r -> Thread(r, "vc-settings").apply { isDaemon = true } }

    @Volatile private var settings: VcSettings = loadSettings()
    @Volatile private var lastCheck = 0L
    @Volatile private var disabled = false
    @Volatile private var nativeReady: Boolean? = null

    fun onStart(record: AudioRecord) {
        refreshSoon(force = true)
        processors[record]?.let { synchronized(it) { it.appliedRevision = -1 } }
    }

    fun onRelease(record: AudioRecord) {
        processors.remove(record)?.let { synchronized(it) { it.close() } }
    }

    /** Converter for this recorder, or null when nothing should be converted. */
    fun processorFor(record: AudioRecord): VoiceProcessor? {
        if (disabled) return null
        refreshSoon(force = false)
        val s = settings
        val convert = s.shouldConvert(packageName)
        var p = processors[record]
        if (p == null) {
            // Don't spin up anything for calls that won't be converted.
            if (!convert || !ensureNative()) return null
            p = VoiceProcessor(record.sampleRate).also { processors[record] = it }
            XposedBridge.log("VoiceChanger: converting ${record.sampleRate} Hz x${record.channelCount}, latency ${p.latencyMs} ms")
        }
        synchronized(p) {
            if (p.isClosed) return null
            if (p.appliedRevision != s.revision) {
                p.setVoice(s.voice())
                p.appliedRevision = s.revision
            }
            // Kept alive while disabled so switching on/off mid-call crossfades instead of clicking.
            p.setEnabled(convert)
        }
        return p
    }

    fun disable(t: Throwable) {
        disabled = true
        XposedBridge.log("VoiceChanger: disabled after error")
        XposedBridge.log(t)
    }

    private fun refreshSoon(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 700) return
        lastCheck = now
        // File I/O stays off the audio thread.
        refresher.execute {
            if (prefs.hasFileChanged()) {
                prefs.reload()
                settings = Prefs.read(prefs)
            }
        }
    }

    private fun loadSettings(): VcSettings {
        if (!prefs.file.canRead()) {
            XposedBridge.log("VoiceChanger: settings not readable; open the app once after enabling the module")
        }
        return Prefs.read(prefs)
    }

    private fun ensureNative(): Boolean {
        nativeReady?.let { return it }
        val ok = libraryCandidates().any { path ->
            try {
                NativeBridge.loadFrom(path)
                true
            } catch (e: Throwable) {
                false
            }
        }
        if (!ok) XposedBridge.log("VoiceChanger: could not load libvoiceengine.so from $modulePath")
        nativeReady = ok
        return ok
    }

    private fun libraryCandidates(): List<String> {
        val apk = modulePath ?: return emptyList()
        val abi = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS.firstOrNull() else Build.SUPPORTED_32_BIT_ABIS.firstOrNull()
        abi ?: return emptyList()
        val dirName = when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            else -> abi
        }
        val extracted = File(File(apk).parentFile, "lib/$dirName/libvoiceengine.so").path
        return listOf(extracted, "$apk!/lib/$abi/libvoiceengine.so")
    }
}
