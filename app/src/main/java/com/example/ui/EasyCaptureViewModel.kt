package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.capture.CaptureStateHolder
import com.example.capture.ScreenCaptureService
import com.example.data.AudioSourceMode
import com.example.data.CaptureSessionState
import com.example.data.CaptureSettings
import com.example.data.NormalizedRect
import com.example.data.RecordingAreaMode
import com.example.data.SettingsRepository
import com.example.data.ThemeMode
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution
import com.example.editor.EditorCropPreset
import com.example.editor.ExportStatus
import com.example.editor.VideoEditorEngine
import com.example.editor.VideoEditorUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EasyCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val editorEngine = VideoEditorEngine()

    val settings: StateFlow<CaptureSettings> = settingsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaptureSettings()
        )

    val captureState: StateFlow<CaptureSessionState> = CaptureStateHolder.state

    private val _editorState = MutableStateFlow(VideoEditorUiState())
    val editorState: StateFlow<VideoEditorUiState> = _editorState.asStateFlow()

    private var countdownJob: Job? = null
    private var exportJob: Job? = null

    // --- Settings Actions ---

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepo.updateThemeMode(mode)
    }

    fun setResolution(resolution: VideoResolution) = viewModelScope.launch {
        settingsRepo.updateResolution(resolution)
    }

    fun setExportFormat(format: VideoExportFormat) = viewModelScope.launch {
        settingsRepo.updateExportFormat(format)
    }

    fun setFrameRate(fps: Int) = viewModelScope.launch {
        settingsRepo.updateFrameRate(fps)
    }

    fun setBitrateMbps(mbps: Int) = viewModelScope.launch {
        settingsRepo.updateBitrateMbps(mbps)
    }

    fun setAudioSource(mode: AudioSourceMode) = viewModelScope.launch {
        settingsRepo.updateAudioSource(mode)
    }

    fun setRecordingArea(mode: RecordingAreaMode, rect: NormalizedRect) = viewModelScope.launch {
        settingsRepo.updateArea(mode, rect)
    }

    fun setCountdownSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepo.updateCountdown(seconds)
    }

    fun setFloatingButtonEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateFloatingEnabled(enabled)
    }

    fun setFloatingButtonOpacity(opacity: Float) = viewModelScope.launch {
        settingsRepo.updateFloatingOpacity(opacity)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateHapticEnabled(enabled)
    }

    fun setShowScreenTouches(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateShowScreenTouches(enabled)
    }

    fun setQuickControlBarEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateQuickControlBarEnabled(enabled)
        com.example.capture.QuickControlNotificationManager.setEnabledPreference(getApplication(), enabled)
    }

    // --- Screen Capture Controls ---

    private var pendingConsent: Pair<Int, Intent>? = null

    fun startRecordingWithConsent(resultCode: Int, resultData: Intent) {
        val currentSettings = settings.value
        countdownJob?.cancel()
        pendingConsent = resultCode to resultData
        countdownJob = viewModelScope.launch {
            val countdown = currentSettings.countdownSeconds
            if (countdown > 0) {
                for (sec in countdown downTo 1) {
                    CaptureStateHolder.setCountdown(sec)
                    delay(1_000L)
                }
            }
            val consent = pendingConsent ?: return@launch
            pendingConsent = null
            launchCaptureService(consent.first, consent.second, currentSettings)
        }
    }

    fun startRecordingImmediately() {
        val consent = pendingConsent ?: return
        pendingConsent = null
        countdownJob?.cancel()
        countdownJob = null
        launchCaptureService(consent.first, consent.second, settings.value)
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        pendingConsent = null
        CaptureStateHolder.setIdle(null)
    }

    private fun launchCaptureService(
        resultCode: Int,
        resultData: Intent,
        cfg: CaptureSettings
    ) {
        val app = getApplication<Application>()
        val areaRect = if (cfg.areaMode == RecordingAreaMode.FULL_SCREEN) {
            NormalizedRect.FULL
        } else {
            cfg.customArea.sanitized()
        }
        val intent = Intent(app, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_RESOLUTION, cfg.resolution.name)
            putExtra(ScreenCaptureService.EXTRA_FORMAT, cfg.exportFormat.name)
            putExtra(ScreenCaptureService.EXTRA_FPS, cfg.frameRate)
            putExtra(ScreenCaptureService.EXTRA_BITRATE_MBPS, cfg.bitrateMbps)
            putExtra(ScreenCaptureService.EXTRA_AUDIO_MODE, cfg.audioSource.name)
            putExtra(ScreenCaptureService.EXTRA_CROP_LEFT, areaRect.left)
            putExtra(ScreenCaptureService.EXTRA_CROP_TOP, areaRect.top)
            putExtra(ScreenCaptureService.EXTRA_CROP_RIGHT, areaRect.right)
            putExtra(ScreenCaptureService.EXTRA_CROP_BOTTOM, areaRect.bottom)
            putExtra(ScreenCaptureService.EXTRA_FLOATING_ENABLED, cfg.floatingButtonEnabled)
            putExtra(ScreenCaptureService.EXTRA_FLOATING_OPACITY, cfg.floatingButtonOpacity)
            putExtra(ScreenCaptureService.EXTRA_HAPTIC_ENABLED, cfg.hapticFeedbackEnabled)
            putExtra(ScreenCaptureService.EXTRA_SHOW_SCREEN_TOUCHES, cfg.showScreenTouches)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun captureStandaloneScreenshotWithConsent(resultCode: Int, resultData: Intent) {
        val app = getApplication<Application>()
        val intent = Intent(app, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_SINGLE_SCREENSHOT
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_HAPTIC_ENABLED, settings.value.hapticFeedbackEnabled)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopRecording() {
        countdownJob?.cancel()
        val app = getApplication<Application>()
        app.startService(
            Intent(app, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
        )
    }

    fun togglePauseRecording() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_TOGGLE_PAUSE)
        )
    }

    fun triggerInAppScreenshot() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_SCREENSHOT)
        )
    }

    fun dismissCaptureBanner() {
        CaptureStateHolder.clearBanner()
    }

    // --- Video Editor Controls ---

    fun loadVideoForEditing(uri: Uri) {
        viewModelScope.launch {
            val previousThumbs = _editorState.value.video?.thumbnails.orEmpty()
            _editorState.update {
                it.copy(
                    isLoadingVideo = true,
                    loadError = null,
                    exportStatus = ExportStatus.Idle
                )
            }
            try {
                val meta = editorEngine.inspectVideo(getApplication(), uri)
                previousThumbs.forEach { if (!it.isRecycled) it.recycle() }
                val currentSettings = settings.value
                _editorState.update {
                    it.copy(
                        isLoadingVideo = false,
                        video = meta,
                        config = it.config.copy(
                            trimStartFraction = 0f,
                            trimEndFraction = 1f,
                            cropPreset = EditorCropPreset.ORIGINAL,
                            cropRect = NormalizedRect.FULL,
                            speed = 1.0f,
                            rotationDegrees = 0,
                            muteAudio = false,
                            exportResolution = currentSettings.resolution,
                            exportFormat = currentSettings.exportFormat,
                            exportFps = currentSettings.frameRate,
                            exportBitrateMbps = currentSettings.bitrateMbps
                        ),
                        loadError = null
                    )
                }
            } catch (e: Exception) {
                _editorState.update {
                    it.copy(
                        isLoadingVideo = false,
                        loadError = "Unable to inspect video: ${e.localizedMessage ?: "Unsupported file"}"
                    )
                }
            }
        }
    }

    fun updateTrimRange(start: Float, end: Float) {
        val safeStart = start.coerceIn(0f, 0.95f)
        val safeEnd = end.coerceIn(safeStart + 0.04f, 1f)
        _editorState.update {
            it.copy(
                config = it.config.copy(trimStartFraction = safeStart, trimEndFraction = safeEnd),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun selectEditorCropPreset(preset: EditorCropPreset) {
        val meta = _editorState.value.video ?: return
        val containerRatio = meta.effectiveWidth.toFloat() / meta.effectiveHeight.toFloat().coerceAtLeast(1f)
        val rect = if (preset.ratio == null) {
            NormalizedRect.FULL
        } else {
            NormalizedRect.forAspectRatio(preset.ratio, containerRatio)
        }
        _editorState.update {
            it.copy(
                config = it.config.copy(cropPreset = preset, cropRect = rect),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun updateEditorSpeed(speed: Float) {
        _editorState.update {
            it.copy(
                config = it.config.copy(speed = speed),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun rotateEditorVideo90() {
        _editorState.update {
            it.copy(
                config = it.config.copy(rotationDegrees = (it.config.rotationDegrees + 90) % 360),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun toggleEditorMute() {
        _editorState.update {
            it.copy(
                config = it.config.copy(muteAudio = !it.config.muteAudio),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun updateEditorExportResolution(resolution: VideoResolution) {
        _editorState.update {
            it.copy(
                config = it.config.copy(exportResolution = resolution),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun updateEditorExportFormat(format: VideoExportFormat) {
        _editorState.update {
            it.copy(
                config = it.config.copy(exportFormat = format),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun updateEditorExportFps(fps: Int) {
        _editorState.update {
            it.copy(
                config = it.config.copy(exportFps = fps),
                exportStatus = ExportStatus.Idle
            )
        }
    }

    fun exportEditedVideo() {
        val current = _editorState.value
        val video = current.video ?: return
        if (current.exportStatus is ExportStatus.Exporting) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _editorState.update {
                it.copy(exportStatus = ExportStatus.Exporting(0.02f, "Initializing HD exporter…"))
            }
            try {
                val saved = editorEngine.exportEditedVideo(
                    context = getApplication(),
                    video = video,
                    config = current.config,
                    onProgress = { progress, stage ->
                        _editorState.update { state ->
                            state.copy(exportStatus = ExportStatus.Exporting(progress, stage))
                        }
                    }
                )
                _editorState.update {
                    it.copy(
                        exportStatus = ExportStatus.Success(
                            fileName = saved.fileName,
                            directory = saved.displayDirectory
                        )
                    )
                }
            } catch (e: Exception) {
                _editorState.update {
                    it.copy(
                        exportStatus = ExportStatus.Error(
                            e.localizedMessage ?: "Export failed. Try MP4 H.264 1080p."
                        )
                    )
                }
            }
        }
    }

    fun dismissExportStatus() {
        _editorState.update { it.copy(exportStatus = ExportStatus.Idle) }
    }

    override fun onCleared() {
        _editorState.value.video?.thumbnails?.forEach { if (!it.isRecycled) it.recycle() }
        super.onCleared()
    }
}
