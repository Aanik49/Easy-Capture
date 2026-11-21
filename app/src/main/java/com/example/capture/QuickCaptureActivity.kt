package com.example.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AudioSourceMode
import com.example.data.CaptureSettings
import com.example.data.NormalizedRect
import com.example.data.RecordingAreaMode
import com.example.data.RecordingPhase
import com.example.data.SettingsRepository
import com.example.ui.components.BroadcastCountdownOverlay
import com.example.ui.theme.EasyCaptureTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Invisible transparent launcher used by the top-bar Quick Control Notification for Record and Screenshot.
 * Runs directly over whatever screen or app the user is currently viewing — NEVER opening Easy Capture's main UI.
 * Reads the user's current saved recording settings, shows the pre-recording countdown overlay, and starts recording.
 */
class QuickCaptureActivity : ComponentActivity() {

    private var activeSettings by mutableStateOf(CaptureSettings())
    private var showCountdownOverlay by mutableStateOf(false)
    private var remainingSeconds by mutableIntStateOf(0)
    private var pendingMode: String = QuickControlNotificationManager.QUICK_ACTION_RECORD
    private var pendingConsent: Pair<Int, Intent>? = null
    private var countdownJob: Job? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchScreenCaptureConsent()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            finishSilently()
            return@registerForActivityResult
        }

        if (pendingMode == QuickControlNotificationManager.QUICK_ACTION_SCREENSHOT) {
            launchStandaloneScreenshotService(result.resultCode, data, activeSettings)
            finishSilently()
        } else {
            beginCountdownAndRecord(result.resultCode, data, activeSettings)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        disableTransitions()

        pendingMode = intent?.getStringExtra(QuickControlNotificationManager.EXTRA_QUICK_ACTION)
            ?: QuickControlNotificationManager.QUICK_ACTION_RECORD

        val currentPhase = CaptureStateHolder.state.value.phase
        if (pendingMode == QuickControlNotificationManager.QUICK_ACTION_SCREENSHOT &&
            (currentPhase == RecordingPhase.RECORDING || currentPhase == RecordingPhase.PAUSED)
        ) {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_SCREENSHOT))
            finishSilently()
            return
        }

        if (pendingMode == QuickControlNotificationManager.QUICK_ACTION_RECORD &&
            currentPhase != RecordingPhase.IDLE
        ) {
            finishSilently()
            return
        }

        setContent {
            EasyCaptureTheme(themeMode = activeSettings.themeMode) {
                BackHandler(enabled = showCountdownOverlay) {
                    cancelCountdownAndFinish()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showCountdownOverlay) {
                        BroadcastCountdownOverlay(
                            remainingSeconds = remainingSeconds,
                            totalSeconds = activeSettings.countdownSeconds.coerceAtLeast(1),
                            settings = activeSettings,
                            onStartNow = { startRecordingImmediately() },
                            onCancel = { cancelCountdownAndFinish() },
                            translucentBackdrop = true
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            val savedSettings = runCatching {
                SettingsRepository(applicationContext).settingsFlow.first()
            }.getOrDefault(CaptureSettings())
            activeSettings = savedSettings

            if (pendingMode == QuickControlNotificationManager.QUICK_ACTION_RECORD) {
                val needsMic = savedSettings.audioSource != AudioSourceMode.MUTE
                val hasMic = ContextCompat.checkSelfPermission(
                    this@QuickCaptureActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (needsMic && !hasMic) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    launchScreenCaptureConsent()
                }
            } else {
                launchScreenCaptureConsent()
            }
        }
    }

    private fun launchScreenCaptureConsent() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun beginCountdownAndRecord(
        resultCode: Int,
        resultData: Intent,
        cfg: CaptureSettings
    ) {
        pendingConsent = resultCode to resultData
        val count = cfg.countdownSeconds
        if (count <= 0) {
            startRecordingImmediately()
            return
        }

        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            showCountdownOverlay = true
            for (sec in count downTo 1) {
                remainingSeconds = sec
                CaptureStateHolder.setCountdown(sec)
                delay(1_000L)
            }
            startRecordingImmediately()
        }
    }

    private fun startRecordingImmediately() {
        val consent = pendingConsent ?: return
        pendingConsent = null
        countdownJob?.cancel()
        countdownJob = null
        showCountdownOverlay = false
        launchCaptureService(consent.first, consent.second, activeSettings)
        finishSilently()
    }

    private fun cancelCountdownAndFinish() {
        countdownJob?.cancel()
        countdownJob = null
        pendingConsent = null
        showCountdownOverlay = false
        CaptureStateHolder.setIdle(null)
        finishSilently()
    }

    private fun launchCaptureService(
        resultCode: Int,
        resultData: Intent,
        cfg: CaptureSettings
    ) {
        val areaRect = if (cfg.areaMode == RecordingAreaMode.FULL_SCREEN) {
            NormalizedRect.FULL
        } else {
            cfg.customArea.sanitized()
        }
        val serviceIntent = Intent(applicationContext, ScreenCaptureService::class.java).apply {
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
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }
    }

    private fun launchStandaloneScreenshotService(
        resultCode: Int,
        resultData: Intent,
        cfg: CaptureSettings
    ) {
        val serviceIntent = Intent(applicationContext, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_SINGLE_SCREENSHOT
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_HAPTIC_ENABLED, cfg.hapticFeedbackEnabled)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }
    }

    private fun finishSilently() {
        finish()
        disableTransitions()
    }

    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        if (pendingConsent != null) {
            countdownJob?.cancel()
            pendingConsent = null
            CaptureStateHolder.setIdle(null)
        }
        super.onDestroy()
    }
}
