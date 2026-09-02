package com.brave.ytmusic.equalizer

/**
 * Frequency band configuration and state snapshot.
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val gainDb: Float // -12.0f to +12.0f
)

/**
 * Equalizer state snapshot including all 5 bands, bass boost, preamp, and active preset.
 */
data class EqualizerState(
    val isEnabled: Boolean = true,
    val selectedPresetName: String = "Flat",
    val bands: List<EqualizerBand> = listOf(
        EqualizerBand(0, 60, "60Hz", 0f),
        EqualizerBand(1, 230, "230Hz", 0f),
        EqualizerBand(2, 910, "910Hz", 0f),
        EqualizerBand(3, 3600, "3.6kHz", 0f),
        EqualizerBand(4, 14000, "14kHz", 0f)
    ),
    val bassBoostDb: Float = 0f, // 0.0f to +10.0f
    val preampGain: Float = 1.0f // 0.5f to 1.5f
)

/**
 * Audio Preset definitions
 */
enum class EqualizerPreset(val presetName: String, val gains: FloatArray, val bassBoost: Float) {
    FLAT("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f), 0f),
    BASS_BOOSTER("Bass Booster", floatArrayOf(6f, 4f, 1f, 0f, 0f), 6f),
    EDM_ELECTRONIC("Electronic", floatArrayOf(5f, 3f, -1f, 3f, 4f), 4f),
    ROCK("Rock", floatArrayOf(4f, 2f, -1f, 2f, 4f), 2f),
    POP("Pop", floatArrayOf(-1f, 2f, 4f, 2f, -1f), 1f),
    VOCAL_BOOSTER("Vocal Booster", floatArrayOf(-2f, 1f, 5f, 3f, 0f), 0f),
    HIP_HOP("Hip-Hop", floatArrayOf(6f, 3f, 0f, 2f, 3f), 5f),
    CLASSICAL("Classical", floatArrayOf(3f, 2f, -1f, 2f, 3f), 0f)
}
