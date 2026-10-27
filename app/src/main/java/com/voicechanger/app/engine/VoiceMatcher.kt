package com.voicechanger.app.engine

/** Measured traits of one speaker: typical pitch and the average shape of their vocal tract. */
class VoiceProfile(val values: List<Float>) {
    val medianF0: Float get() = values.getOrElse(0) { 0f }
    val voicedSeconds: Float get() = values.getOrElse(1) { 0f }

    /** Mirrors VoiceProfile::valid() in the engine: needs a pitch and at least a second of speech. */
    val isValid: Boolean get() = values.size == VoiceMatcher.PROFILE_SIZE && medianF0 > 0f && voicedSeconds >= 1f
}

/**
 * Approximates a target speaker from a recording: pitch, vocal-tract size and
 * overall tone are measured from both voices and turned into engine settings.
 */
object VoiceMatcher {
    const val EQ_BANDS = 24
    const val PROFILE_SIZE = 2 + 128

    fun analyze(pcm: ShortArray, length: Int, sampleRate: Int): VoiceProfile =
        VoiceProfile(NativeBridge.nativeAnalyze(pcm, length, sampleRate).toList())

    fun match(own: VoiceProfile, target: VoiceProfile): VoiceParams {
        val m = NativeBridge.nativeMatch(own.values.toFloatArray(), target.values.toFloatArray())
        return VoiceParams(
            pitchSemitones = m[0],
            formantRatio = m[1],
            breathiness = m[2],
            eqDb = m.copyOfRange(3, 3 + EQ_BANDS).toList(),
        )
    }
}
