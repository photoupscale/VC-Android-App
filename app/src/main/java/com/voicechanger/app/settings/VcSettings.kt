package com.voicechanger.app.settings

import android.content.SharedPreferences
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoicePresets

/** Apps whose calls can be converted. Must match res/values/arrays.xml xposed_scope. */
enum class TargetApp(val packageName: String, val label: String) {
    WhatsApp("com.whatsapp", "WhatsApp"),
    WhatsAppBusiness("com.whatsapp.w4b", "WhatsApp Business"),
    Messenger("com.facebook.orca", "Messenger"),
    Facebook("com.facebook.katana", "Facebook"),
    ;

    companion object {
        val packageNames = entries.map { it.packageName }.toSet()
    }
}

/**
 * Everything the call hook needs. Stored in a world-readable SharedPreferences
 * file that LSPosed exposes to the hooked apps through XSharedPreferences.
 */
data class VcSettings(
    /** Master switch. */
    val enabled: Boolean = true,
    /** Convert every call automatically in the background, without opening the app. */
    val autoActivate: Boolean = true,
    /** Manual mode only: convert right now. Toggled from the app or the Quick Settings tile. */
    val activeNow: Boolean = false,
    val apps: Set<String> = setOf(TargetApp.WhatsApp.packageName, TargetApp.Messenger.packageName),
    val presetId: String = "f_soft",
    val custom: VoiceParams = VoiceParams(pitchSemitones = 5f, formantRatio = 1.15f),
    /** Bumped on every save so hooked processes can tell when to re-apply. */
    val revision: Long = 0,
) {
    fun voice(): VoiceParams =
        if (presetId == VoicePresets.CUSTOM_ID) custom else VoicePresets.byId(presetId)?.params ?: custom

    fun shouldConvert(packageName: String): Boolean =
        enabled && packageName in apps && (autoActivate || activeNow)

    /** What the Quick Settings tile toggles: master switch in auto mode, "active now" in manual mode. */
    val tileOn: Boolean get() = if (autoActivate) enabled else enabled && activeNow
}

object Prefs {
    const val FILE = "vc_settings"

    private const val ENABLED = "enabled"
    private const val AUTO = "auto_activate"
    private const val ACTIVE_NOW = "active_now"
    private const val APPS = "apps"
    private const val PRESET = "preset"
    private const val C_PITCH = "custom_pitch"
    private const val C_FORMANT = "custom_formant"
    private const val C_BRIGHT = "custom_brightness"
    private const val REVISION = "revision"

    fun read(p: SharedPreferences): VcSettings {
        val d = VcSettings()
        return VcSettings(
            enabled = p.getBoolean(ENABLED, d.enabled),
            autoActivate = p.getBoolean(AUTO, d.autoActivate),
            activeNow = p.getBoolean(ACTIVE_NOW, d.activeNow),
            apps = p.getStringSet(APPS, null)?.toSet() ?: d.apps,
            presetId = p.getString(PRESET, null) ?: d.presetId,
            custom = VoiceParams(
                pitchSemitones = p.getFloat(C_PITCH, d.custom.pitchSemitones),
                formantRatio = p.getFloat(C_FORMANT, d.custom.formantRatio),
                brightnessDb = p.getFloat(C_BRIGHT, d.custom.brightnessDb),
            ),
            revision = p.getLong(REVISION, 0L),
        )
    }

    fun write(p: SharedPreferences, s: VcSettings) {
        p.edit()
            .putBoolean(ENABLED, s.enabled)
            .putBoolean(AUTO, s.autoActivate)
            .putBoolean(ACTIVE_NOW, s.activeNow)
            .putStringSet(APPS, s.apps)
            .putString(PRESET, s.presetId)
            .putFloat(C_PITCH, s.custom.pitchSemitones)
            .putFloat(C_FORMANT, s.custom.formantRatio)
            .putFloat(C_BRIGHT, s.custom.brightnessDb)
            .putLong(REVISION, s.revision)
            .commit()
    }
}
