package com.voicechanger.app.engine

data class VoiceParams(
    val pitchSemitones: Float,
    val formantRatio: Float,
    val brightnessDb: Float = 0f,
    val breathiness: Float = 0f,
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
 * formants move together but not equally (roughly 3% formant per semitone),
 * which is what keeps them sounding human. Female voices also get a touch of
 * breathiness, which real female voices have and shifted male voices lack.
 */
object VoicePresets {
    const val CUSTOM_ID = "custom"

    private fun female(id: String, name: String, description: String, pitch: Float, formant: Float, bright: Float, breath: Float) =
        VoicePreset(id, name, description, VoiceCategory.MaleToFemale, VoiceParams(pitch, formant, bright, breath))

    private fun male(id: String, name: String, description: String, pitch: Float, formant: Float, bright: Float, breath: Float = 0f) =
        VoicePreset(id, name, description, VoiceCategory.FemaleToMale, VoiceParams(pitch, formant, bright, breath))

    private fun funVoice(id: String, name: String, description: String, pitch: Float, formant: Float, bright: Float, breath: Float = 0f) =
        VoicePreset(id, name, description, VoiceCategory.Fun, VoiceParams(pitch, formant, bright, breath))

    val all = listOf(
        // Male -> female: 24 voices, from low alto to light soprano.
        female("f_soft", "Soft", "Natural, gentle adult woman", 5f, 1.16f, 1.5f, 0.18f),
        female("f_sweet", "Sweet", "Friendly and warm, a smile in the voice", 6f, 1.18f, 2f, 0.20f),
        female("f_young", "Young", "Lighter and brighter, early twenties", 7f, 1.21f, 2.5f, 0.15f),
        female("f_teen", "Teen", "Light, high and quick", 8f, 1.24f, 3f, 0.12f),
        female("f_college", "College", "Casual, clear student voice", 6.5f, 1.19f, 2f, 0.14f),
        female("f_mature", "Mature", "Lower, warmer adult woman", 4f, 1.12f, 0.5f, 0.15f),
        female("f_elegant", "Elegant", "Smooth, composed and polished", 4.5f, 1.14f, 1f, 0.12f),
        female("f_husky", "Husky", "Low, smoky alto", 3f, 1.10f, -1f, 0.30f),
        female("f_smoky", "Smoky", "Dark and relaxed, late night radio", 3.5f, 1.09f, -1.5f, 0.35f),
        female("f_breathy", "Breathy", "Soft and airy, close to the mic", 5f, 1.15f, 1f, 0.40f),
        female("f_alto", "Warm alto", "Rich, low female voice", 3.5f, 1.11f, 0f, 0.20f),
        female("f_deep_alto", "Deep alto", "The lowest natural female range", 2.5f, 1.08f, -0.5f, 0.22f),
        female("f_cheerful", "Cheerful", "Bright and energetic", 7f, 1.20f, 3f, 0.10f),
        female("f_shy", "Shy", "Quiet, soft and a little hesitant", 6f, 1.17f, 0.5f, 0.32f),
        female("f_calm", "Calm", "Soothing and unhurried", 4.5f, 1.13f, 0f, 0.22f),
        female("f_gentle", "Gentle", "Kind and tender", 5.5f, 1.16f, 1f, 0.25f),
        female("f_crisp", "Crisp", "Clear, precise, professional", 5f, 1.15f, 2.5f, 0.08f),
        female("f_confident", "Confident", "Firm and assured", 4f, 1.13f, 1.5f, 0.08f),
        female("f_host", "Radio host", "Polished broadcast voice", 4.5f, 1.12f, 1.5f, 0.10f),
        female("f_narrator", "Narrator", "Even, storytelling voice", 4f, 1.12f, 0.5f, 0.14f),
        female("f_auntie", "Auntie", "Older, warm and motherly", 3f, 1.10f, 0f, 0.18f),
        female("f_lively", "Lively", "Expressive and animated", 6.5f, 1.20f, 2.5f, 0.12f),
        female("f_petite", "Petite", "Small, light and delicate", 7.5f, 1.25f, 2f, 0.18f),
        female("f_soprano", "Light soprano", "High and clear", 8.5f, 1.25f, 3f, 0.12f),

        // Female -> male: 8 voices.
        male("m_deep", "Deep", "Low, resonant adult man", -6f, 0.84f, -2f),
        male("m_warm", "Warm", "Relaxed, everyday adult man", -5f, 0.87f, -1f),
        male("m_young", "Young", "Lighter teen or early twenties", -4f, 0.90f, 0f),
        male("m_baritone", "Baritone", "Very deep, broadcast style", -7f, 0.81f, -3f),
        male("m_calm", "Calm", "Soft-spoken, steady", -5f, 0.88f, -1.5f, 0.10f),
        male("m_husky", "Husky", "Rough, low and breathy", -6f, 0.85f, -2f, 0.20f),
        male("m_clear", "Clear", "Bright, articulate adult man", -4.5f, 0.89f, 1f),
        male("m_mature", "Mature", "Older, settled voice", -6.5f, 0.83f, -1f, 0.08f),

        // Fun
        funVoice("x_child", "Child", "Small child", 9f, 1.30f, 2f, 0.05f),
        funVoice("x_giant", "Giant", "Huge, slow-sounding voice", -10f, 0.74f, -4f),
        funVoice("x_chipmunk", "Chipmunk", "Classic sped-up cartoon", 11f, 1.05f, 2f),
        funVoice("x_monster", "Monster", "Dark and growly", -12f, 0.70f, -5f, 0.25f),
    )

    fun byId(id: String): VoicePreset? = all.firstOrNull { it.id == id }
}
