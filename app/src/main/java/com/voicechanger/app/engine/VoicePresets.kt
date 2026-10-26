package com.voicechanger.app.engine

data class VoiceParams(
    val pitchSemitones: Float,
    val formantRatio: Float,
    val brightnessDb: Float = 0f,
    val mix: Float = 1f,
)

enum class VoiceCategory(val title: String) {
    MaleToFemale("Male to female"),
    FemaleToMale("Female to male"),
    Fun("Fun"),
}

data class VoicePreset(
    val id: String,
    val name: String,
    val description: String,
    val category: VoiceCategory,
    val params: VoiceParams,
)

/**
 * Starting points tuned around typical speaking ranges: adult male ~85-155 Hz,
 * adult female ~165-255 Hz, with female vocal tracts ~15-20% shorter. Pitch and
 * formants move together but not equally, which is what keeps them sounding human.
 */
object VoicePresets {
    const val CUSTOM_ID = "custom"

    val all = listOf(
        VoicePreset("f_soft", "Soft female", "Natural, gentle adult woman", VoiceCategory.MaleToFemale,
            VoiceParams(pitchSemitones = 5f, formantRatio = 1.16f, brightnessDb = 1.5f)),
        VoicePreset("f_young", "Young female", "Lighter, brighter, early twenties", VoiceCategory.MaleToFemale,
            VoiceParams(pitchSemitones = 7f, formantRatio = 1.21f, brightnessDb = 2.5f)),
        VoicePreset("f_mature", "Mature female", "Lower, warmer adult woman", VoiceCategory.MaleToFemale,
            VoiceParams(pitchSemitones = 4f, formantRatio = 1.12f, brightnessDb = 0.5f)),
        VoicePreset("f_husky", "Husky female", "Low, smoky alto", VoiceCategory.MaleToFemale,
            VoiceParams(pitchSemitones = 3f, formantRatio = 1.10f, brightnessDb = -1f)),

        VoicePreset("m_deep", "Deep male", "Low, resonant adult man", VoiceCategory.FemaleToMale,
            VoiceParams(pitchSemitones = -6f, formantRatio = 0.84f, brightnessDb = -2f)),
        VoicePreset("m_warm", "Warm male", "Relaxed, everyday adult man", VoiceCategory.FemaleToMale,
            VoiceParams(pitchSemitones = -5f, formantRatio = 0.87f, brightnessDb = -1f)),
        VoicePreset("m_young", "Young male", "Lighter teen or early twenties", VoiceCategory.FemaleToMale,
            VoiceParams(pitchSemitones = -4f, formantRatio = 0.90f, brightnessDb = 0f)),
        VoicePreset("m_baritone", "Baritone", "Very deep, broadcast style", VoiceCategory.FemaleToMale,
            VoiceParams(pitchSemitones = -7f, formantRatio = 0.81f, brightnessDb = -3f)),

        VoicePreset("x_child", "Child", "Small child", VoiceCategory.Fun,
            VoiceParams(pitchSemitones = 9f, formantRatio = 1.30f, brightnessDb = 2f)),
        VoicePreset("x_giant", "Giant", "Huge, slow-sounding voice", VoiceCategory.Fun,
            VoiceParams(pitchSemitones = -10f, formantRatio = 0.74f, brightnessDb = -4f)),
    )

    fun byId(id: String): VoicePreset? = all.firstOrNull { it.id == id }
}
