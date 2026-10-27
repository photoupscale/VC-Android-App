package com.voicechanger.app.settings

import android.content.SharedPreferences
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoicePresets
import org.json.JSONArray
import org.json.JSONObject

/** A voice the user made from a recording of someone. */
data class MyVoice(val id: String, val name: String, val params: VoiceParams) {
    val presetId: String get() = PREFIX + id

    companion object {
        const val PREFIX = "my_"
    }
}

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
    val custom: VoiceParams = VoiceParams(pitchSemitones = 5f, formantRatio = 1.15f, breathiness = 0.15f),
    val myVoices: List<MyVoice> = emptyList(),
    /** The user's own voice profile (see VoiceMatcher), needed to match new voices. */
    val ownProfile: List<Float>? = null,
    /** Bumped on every save so hooked processes can tell when to re-apply. */
    val revision: Long = 0,
) {
    fun voice(): VoiceParams = when {
        presetId == VoicePresets.CUSTOM_ID -> custom
        presetId.startsWith(MyVoice.PREFIX) -> myVoices.firstOrNull { it.presetId == presetId }?.params ?: custom
        else -> VoicePresets.byId(presetId)?.params ?: custom
    }

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
    private const val C_BREATH = "custom_breathiness"
    private const val MY_VOICES = "my_voices"
    private const val OWN_PROFILE = "own_profile"
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
                breathiness = p.getFloat(C_BREATH, d.custom.breathiness),
            ),
            myVoices = runCatching { decodeVoices(p.getString(MY_VOICES, null)) }.getOrDefault(emptyList()),
            ownProfile = p.getString(OWN_PROFILE, null)?.let { runCatching { decodeFloats(JSONArray(it)) }.getOrNull() },
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
            .putFloat(C_BREATH, s.custom.breathiness)
            .putString(MY_VOICES, encodeVoices(s.myVoices))
            .putString(OWN_PROFILE, s.ownProfile?.let { encodeFloats(it).toString() })
            .putLong(REVISION, s.revision)
            // apply() writes in the background; commit() here blocked the UI on every slider move.
            .apply()
    }

    private fun encodeFloats(values: List<Float>) = JSONArray().apply { values.forEach { put(it.toDouble()) } }

    private fun decodeFloats(a: JSONArray) = List(a.length()) { a.getDouble(it).toFloat() }

    private fun encodeVoices(voices: List<MyVoice>): String = JSONArray().apply {
        voices.forEach { v ->
            put(
                JSONObject()
                    .put("id", v.id)
                    .put("name", v.name)
                    .put("pitch", v.params.pitchSemitones.toDouble())
                    .put("formant", v.params.formantRatio.toDouble())
                    .put("brightness", v.params.brightnessDb.toDouble())
                    .put("breathiness", v.params.breathiness.toDouble())
                    .put("eq", v.params.eqDb?.let(::encodeFloats)),
            )
        }
    }.toString()

    private fun decodeVoices(json: String?): List<MyVoice> {
        if (json.isNullOrEmpty()) return emptyList()
        val a = JSONArray(json)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            MyVoice(
                id = o.getString("id"),
                name = o.getString("name"),
                params = VoiceParams(
                    pitchSemitones = o.getDouble("pitch").toFloat(),
                    formantRatio = o.getDouble("formant").toFloat(),
                    brightnessDb = o.optDouble("brightness", 0.0).toFloat(),
                    breathiness = o.optDouble("breathiness", 0.0).toFloat(),
                    eqDb = o.optJSONArray("eq")?.let(::decodeFloats),
                ),
            )
        }
    }
}
