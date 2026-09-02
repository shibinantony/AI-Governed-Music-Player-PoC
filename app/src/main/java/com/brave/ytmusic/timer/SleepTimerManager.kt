package com.brave.ytmusic.timer

import com.brave.ytmusic.bridge.WebInterfaceBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max

/**
 * State snapshot for active Sleep Timer.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isFadingOut: Boolean = false
)

/**
 * Manages the background sleep countdown with exponential audio attenuation.
 */
class SleepTimerManager(
    private val coroutineScope: CoroutineScope,
    private val bridgeProvider: () -> WebInterfaceBridge?
) {

    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    private var countdownJob: Job? = null
    private val fadeDurationSeconds = 30L

    /**
     * Starts the sleep timer with the specified minute preset.
     */
    fun startTimer(minutes: Int) {
        startCustomTimer(minutes * 60L)
    }

    /**
     * Starts the sleep timer with total duration in seconds.
     */
    fun startCustomTimer(totalSeconds: Long) {
        if (totalSeconds <= 0) return

        cancelTimer()

        countdownJob = coroutineScope.launch(Dispatchers.Default) {
            var remaining = totalSeconds
            _timerState.value = SleepTimerState(
                isActive = true,
                remainingSeconds = remaining,
                totalSeconds = totalSeconds,
                isFadingOut = false
            )

            while (remaining > 0) {
                delay(1000L)
                remaining--

                val isFading = remaining <= fadeDurationSeconds
                if (isFading) {
                    val fadeProgress = remaining.toFloat() / max(1L, fadeDurationSeconds).toFloat()
                    // Smooth exponential decay curve: e^(3*(progress - 1))
                    val exponentialVolume = exp(3.0 * (fadeProgress - 1.0)).toFloat().coerceIn(0.0f, 1.0f)
                    bridgeProvider()?.setVolume(exponentialVolume)
                }

                _timerState.value = SleepTimerState(
                    isActive = true,
                    remainingSeconds = remaining,
                    totalSeconds = totalSeconds,
                    isFadingOut = isFading
                )
            }

            // Timer Expired: Pause playback and reset volume
            bridgeProvider()?.pause()
            delay(500L)
            bridgeProvider()?.setVolume(1.0f)

            _timerState.value = SleepTimerState(isActive = false, remainingSeconds = 0, totalSeconds = 0)
        }
    }

    /**
     * Cancels any active countdown and restores full volume.
     */
    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        bridgeProvider()?.setVolume(1.0f)
        _timerState.value = SleepTimerState(isActive = false, remainingSeconds = 0, totalSeconds = 0)
    }
}
