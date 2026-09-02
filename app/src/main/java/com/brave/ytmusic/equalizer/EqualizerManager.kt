package com.brave.ytmusic.equalizer

import com.brave.ytmusic.bridge.WebInterfaceBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages 5-band parametric equalizer DSP state and synchronizes audio filters with WebView.
 */
class EqualizerManager(private val bridgeProvider: () -> WebInterfaceBridge?) {

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    fun applyPreset(preset: EqualizerPreset) {
        val updatedBands = _equalizerState.value.bands.mapIndexed { index, band ->
            band.copy(gainDb = preset.gains.getOrElse(index) { 0f })
        }
        _equalizerState.value = _equalizerState.value.copy(
            selectedPresetName = preset.presetName,
            bands = updatedBands,
            bassBoostDb = preset.bassBoost
        )
        syncWithEngine()
    }

    fun setBandGain(index: Int, gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        val updatedBands = _equalizerState.value.bands.map { band ->
            if (band.index == index) band.copy(gainDb = clamped) else band
        }
        _equalizerState.value = _equalizerState.value.copy(
            selectedPresetName = "Custom",
            bands = updatedBands
        )
        syncWithEngine()
    }

    fun setBassBoost(boostDb: Float) {
        val clamped = boostDb.coerceIn(0f, 10f)
        _equalizerState.value = _equalizerState.value.copy(
            selectedPresetName = "Custom",
            bassBoostDb = clamped
        )
        syncWithEngine()
    }

    fun setPreampGain(gain: Float) {
        val clamped = gain.coerceIn(0.5f, 1.5f)
        _equalizerState.value = _equalizerState.value.copy(preampGain = clamped)
        syncWithEngine()
    }

    fun resetToFlat() {
        applyPreset(EqualizerPreset.FLAT)
    }

    fun syncWithEngine() {
        val state = _equalizerState.value
        val bandGains = FloatArray(5) { i -> state.bands.getOrNull(i)?.gainDb ?: 0f }
        bridgeProvider()?.setEqualizer(bandGains, state.bassBoostDb, state.preampGain)
    }
}
