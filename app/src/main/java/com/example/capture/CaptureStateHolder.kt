package com.example.capture

import com.example.data.CaptureSessionState
import com.example.data.RecordingPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CaptureStateHolder {
    private val _state = MutableStateFlow(CaptureSessionState())
    val state: StateFlow<CaptureSessionState> = _state.asStateFlow()

    fun setCountdown(remaining: Int) {
        _state.update {
            it.copy(
                phase = RecordingPhase.COUNTDOWN,
                countdownRemaining = remaining,
                errorMessage = null
            )
        }
    }

    fun setRecordingStarted() {
        _state.update {
            it.copy(
                phase = RecordingPhase.RECORDING,
                countdownRemaining = 0,
                elapsedMs = 0L,
                screenshotCount = 0,
                errorMessage = null
            )
        }
    }

    fun setPaused(paused: Boolean) {
        _state.update {
            if (it.phase == RecordingPhase.RECORDING || it.phase == RecordingPhase.PAUSED) {
                it.copy(phase = if (paused) RecordingPhase.PAUSED else RecordingPhase.RECORDING)
            } else {
                it
            }
        }
    }

    fun updateElapsed(elapsedMs: Long) {
        _state.update { it.copy(elapsedMs = elapsedMs) }
    }

    fun updateAudioLevel(level: Float) {
        _state.update { it.copy(liveAudioLevel = level.coerceIn(0f, 1f)) }
    }

    fun recordScreenshotSaved(fileName: String, directory: String) {
        _state.update {
            it.copy(
                screenshotCount = it.screenshotCount + 1,
                lastSavedFileName = fileName,
                lastSavedDirectory = directory,
                lastSavedType = "1080p Screenshot",
                errorMessage = null
            )
        }
    }

    fun recordVideoSaved(fileName: String, directory: String) {
        _state.update {
            it.copy(
                phase = RecordingPhase.IDLE,
                countdownRemaining = 0,
                elapsedMs = 0L,
                liveAudioLevel = 0f,
                screenshotCount = 0,
                lastSavedFileName = fileName,
                lastSavedDirectory = directory,
                lastSavedType = "Screen Recording",
                errorMessage = null
            )
        }
    }

    fun setIdle(error: String? = null) {
        _state.update {
            it.copy(
                phase = RecordingPhase.IDLE,
                countdownRemaining = 0,
                elapsedMs = 0L,
                liveAudioLevel = 0f,
                screenshotCount = 0,
                errorMessage = error
            )
        }
    }

    fun clearBanner() {
        _state.update {
            it.copy(
                lastSavedFileName = null,
                lastSavedDirectory = null,
                lastSavedType = null,
                errorMessage = null
            )
        }
    }
}
