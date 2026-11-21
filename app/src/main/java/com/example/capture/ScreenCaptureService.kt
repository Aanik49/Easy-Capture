package com.example.capture

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AudioSourceMode
import com.example.data.MediaStorageHelper
import com.example.data.NormalizedRect
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var glPipeline: GlScreenPipeline? = null
    private var muxerCoordinator: MuxerCoordinator? = null
    private var audioEngine: RealtimeAudioEngine? = null
    private var floatingShutter: FloatingShutterController? = null

    private var tempOutputFile: File? = null
    private var activeExportFormat: VideoExportFormat = VideoExportFormat.MP4_H264
    private var hapticEnabled: Boolean = true
    private var previousSystemShowTouches: Int? = null

    private val isRecording = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)
    private val isCapturingShot = AtomicBoolean(false)
    @Volatile
    private var isPaused = false

    private var timerJob: Job? = null
    private var elapsedAccumulatedMs = 0L
    private var resumeEpochMs = 0L

    private val videoDrainLock = Any()
    private val videoBufferInfo = MediaCodec.BufferInfo()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCaptureAndSave()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCaptureFromIntent(intent)
            ACTION_STOP -> stopCaptureAndSave()
            ACTION_TOGGLE_PAUSE -> togglePause()
            ACTION_SCREENSHOT -> triggerStealth1080pScreenshot()
            ACTION_SINGLE_SCREENSHOT -> captureStandalone1080pScreenshot(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCaptureFromIntent(intent: Intent) {
        if (isRecording.get()) return
        isStopping.set(false)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            CaptureStateHolder.setIdle("Screen capture permission was not granted.")
            stopSelf()
            return
        }

        val resolution = intent.getStringExtra(EXTRA_RESOLUTION)
            ?.let { runCatching { VideoResolution.valueOf(it) }.getOrNull() }
            ?: VideoResolution.FHD_1080P
        val requestedFormat = intent.getStringExtra(EXTRA_FORMAT)
            ?.let { runCatching { VideoExportFormat.valueOf(it) }.getOrNull() }
            ?: VideoExportFormat.MP4_H264
        val fps = intent.getIntExtra(EXTRA_FPS, 60).coerceIn(24, 60)
        val bitrateMbps = intent.getIntExtra(EXTRA_BITRATE_MBPS, 16).coerceIn(4, 36)
        val audioMode = intent.getStringExtra(EXTRA_AUDIO_MODE)
            ?.let { runCatching { AudioSourceMode.valueOf(it) }.getOrNull() }
            ?: AudioSourceMode.MICROPHONE
        val cropRect = NormalizedRect(
            left = intent.getFloatExtra(EXTRA_CROP_LEFT, 0f),
            top = intent.getFloatExtra(EXTRA_CROP_TOP, 0f),
            right = intent.getFloatExtra(EXTRA_CROP_RIGHT, 1f),
            bottom = intent.getFloatExtra(EXTRA_CROP_BOTTOM, 1f)
        ).sanitized()
        val floatingEnabled = intent.getBooleanExtra(EXTRA_FLOATING_ENABLED, true)
        val floatingOpacity = intent.getFloatExtra(EXTRA_FLOATING_OPACITY, 0.82f)
        hapticEnabled = intent.getBooleanExtra(EXTRA_HAPTIC_ENABLED, true)
        val showScreenTouches = intent.getBooleanExtra(EXTRA_SHOW_SCREEN_TOUCHES, false)

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val effectiveAudioMode = when {
            !requestedFormat.supportsAacAudio -> AudioSourceMode.MUTE
            audioMode == AudioSourceMode.MUTE -> AudioSourceMode.MUTE
            !hasMicPermission -> AudioSourceMode.MUTE
            else -> audioMode
        }

        // Start Foreground Service BEFORE acquiring MediaProjection (required on Android 14+)
        ensureNotificationChannel()
        QuickControlNotificationManager.hideDuringRecording(this)
        val notification = buildNotification(isPaused = false)
        startForegroundCompat(notification, includeMicrophone = effectiveAudioMode != AudioSourceMode.MUTE)
        applySystemShowTouches(enable = showScreenTouches)

        try {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection token expired")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, mainHandler)

            val (displayW, displayH, densityDpi) = readDisplayMetrics()
            val (desiredW, desiredH) = computeEncodeDimensions(displayW, displayH, cropRect, resolution)

            // Configure video encoder (preserving exact aspect ratio with safe codec fallback if needed)
            val encoderResult = createVideoEncoder(
                requestedFormat = requestedFormat,
                desiredWidth = desiredW,
                desiredHeight = desiredH,
                fps = fps,
                bitrateBps = bitrateMbps * 1_000_000
            )
            val codec = encoderResult.codec
            val resolvedFormat = encoderResult.format
            val encodeW = encoderResult.width
            val encodeH = encoderResult.height
            videoEncoder = codec
            activeExportFormat = resolvedFormat

            val useAudio = effectiveAudioMode != AudioSourceMode.MUTE && resolvedFormat.supportsAacAudio
            val tempFile = File(cacheDir, "ec_rec_${System.currentTimeMillis()}.${resolvedFormat.fileExtension}")
            tempOutputFile = tempFile

            val muxer = MuxerCoordinator(
                outputPath = tempFile.absolutePath,
                outputFormat = resolvedFormat.muxerFormat,
                expectAudioTrack = useAudio
            )
            muxerCoordinator = muxer

            val encoderSurface = codec.createInputSurface()
            codec.start()

            val pipeline = GlScreenPipeline(
                displayWidth = displayW,
                displayHeight = displayH,
                encodeWidth = encodeW,
                encodeHeight = encodeH,
                cropRect = cropRect,
                encoderInputSurface = encoderSurface,
                onEncodedFrameAvailable = { drainVideoEncoder(endOfStream = false) }
            )
            glPipeline = pipeline
            val virtualInputSurface = pipeline.initializeAndGetInputSurface()

            virtualDisplay = projection.createVirtualDisplay(
                "EasyCaptureDisplay",
                displayW,
                displayH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                virtualInputSurface,
                null,
                mainHandler
            )

            if (useAudio) {
                val audio = RealtimeAudioEngine(
                    mode = effectiveAudioMode,
                    mediaProjection = projection,
                    muxer = muxer,
                    onLevelUpdated = { level -> CaptureStateHolder.updateAudioLevel(level) }
                )
                audioEngine = audio
                audio.start()
            }

            if (floatingEnabled) {
                floatingShutter = FloatingShutterController(
                    context = this,
                    initialOpacity = floatingOpacity,
                    onScreenshotClicked = { triggerStealth1080pScreenshot() },
                    onPauseResumeClicked = { togglePause() },
                    onStopClicked = { stopCaptureAndSave() }
                ).also { it.show() }
            }

            isRecording.set(true)
            isPaused = false
            elapsedAccumulatedMs = 0L
            resumeEpochMs = System.currentTimeMillis()
            CaptureStateHolder.setRecordingStarted()
            startTimerLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording", e)
            cleanupResources()
            CaptureStateHolder.setIdle("Could not start recording: ${e.localizedMessage ?: "Unsupported encoder"}")
            stopSelf()
        }
    }

    private fun startForegroundCompat(notification: Notification, includeMicrophone: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (includeMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun togglePause() {
        if (!isRecording.get()) return
        val nextPaused = !isPaused
        isPaused = nextPaused
        if (nextPaused) {
            elapsedAccumulatedMs += (System.currentTimeMillis() - resumeEpochMs).coerceAtLeast(0L)
        } else {
            resumeEpochMs = System.currentTimeMillis()
        }
        glPipeline?.setPausedState(nextPaused)
        audioEngine?.setPausedState(nextPaused)
        floatingShutter?.setPausedState(nextPaused)
        CaptureStateHolder.setPaused(nextPaused)
        updateNotification(nextPaused)
        if (hapticEnabled) performSubtleHaptic()
    }

    /**
     * Captures a crisp 1080p screenshot silently during recording:
     * 1. Holds video encoder frame submission so hiding/restoring the button never appears in the recording.
     * 2. Hides the floating overlay button cleanly before grabbing the GPU frame.
     * 3. Captures exact 1080p PNG directly to Pictures/EasyCapture/EasyCapture_<timestamp>.png.
     * 4. Restores button and emits gentle haptic confirmation without hampering the user.
     */
    private fun triggerStealth1080pScreenshot() {
        if (!isRecording.get() || !isCapturingShot.compareAndSet(false, true)) return
        val pipeline = glPipeline ?: run {
            isCapturingShot.set(false)
            return
        }

        // Freeze encoder submission while hiding overlay so screenshot trigger is invisible in video
        pipeline.holdEncoderFrames = true
        floatingShutter?.setHiddenForScreenshot(true)

        mainHandler.postDelayed({
            pipeline.captureClean1080pScreenshot { bitmap ->
                mainHandler.post {
                    floatingShutter?.setHiddenForScreenshot(false)
                    pipeline.holdEncoderFrames = false
                }
                if (bitmap != null) {
                    serviceScope.launch {
                        try {
                            val saved = MediaStorageHelper.saveScreenshotToDeviceStorage(
                                context = this@ScreenCaptureService,
                                bitmap = bitmap
                            )
                            CaptureStateHolder.recordScreenshotSaved(saved.fileName, saved.displayDirectory)
                            if (hapticEnabled) performSubtleHaptic()
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot save failed", e)
                        } finally {
                            bitmap.recycle()
                            isCapturingShot.set(false)
                        }
                    }
                } else {
                    isCapturingShot.set(false)
                }
            }
        }, 40L)
    }

    private fun stopCaptureAndSave() {
        if (!isStopping.compareAndSet(false, true)) return
        isRecording.set(false)
        timerJob?.cancel()
        timerJob = null

        floatingShutter?.dismiss()
        floatingShutter = null

        serviceScope.launch {
            try {
                audioEngine?.stop()
                audioEngine = null

                runCatching { videoEncoder?.signalEndOfInputStream() }
                drainVideoEncoder(endOfStream = true)

                val hadSamples = muxerCoordinator?.stopAndRelease() ?: false
                muxerCoordinator = null

                cleanupCoreMedia()

                val file = tempOutputFile
                if (hadSamples && file != null && file.exists() && file.length() > 1024L) {
                    val saved = MediaStorageHelper.publishVideoToDeviceStorage(
                        context = this@ScreenCaptureService,
                        tempVideoFile = file,
                        format = activeExportFormat
                    )
                    CaptureStateHolder.recordVideoSaved(saved.fileName, saved.displayDirectory)
                    if (hapticEnabled) performSubtleHaptic()
                } else {
                    CaptureStateHolder.setIdle(null)
                }
                runCatching { file?.delete() }
                tempOutputFile = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                cleanupResources()
                CaptureStateHolder.setIdle("Save failed: ${e.localizedMessage ?: "Storage error"}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                QuickControlNotificationManager.restoreAfterRecording(this@ScreenCaptureService)
                stopSelf()
            }
        }
    }

    /**
     * Captures a single 1080p screenshot when triggered from the persistent top bar while idle.
     */
    private fun captureStandalone1080pScreenshot(intent: Intent) {
        if (isRecording.get()) {
            triggerStealth1080pScreenshot()
            return
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return
        }
        hapticEnabled = intent.getBooleanExtra(EXTRA_HAPTIC_ENABLED, true)

        ensureNotificationChannel()
        QuickControlNotificationManager.hideDuringRecording(this)
        startForegroundCompat(buildNotification(isPaused = false), includeMicrophone = false)

        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = pm.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection token expired")
            val (displayW, displayH, densityDpi) = readDisplayMetrics()
            val isLandscape = displayW >= displayH
            val targetW: Int
            val targetH: Int
            if (isLandscape) {
                targetH = 1080
                targetW = ((displayW.toFloat() / displayH.toFloat()) * 1080f).roundToInt().coerceIn(1080, 3840) and 1.inv()
            } else {
                targetW = 1080
                targetH = ((displayH.toFloat() / displayW.toFloat()) * 1080f).roundToInt().coerceIn(1080, 3840) and 1.inv()
            }

            val reader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
            val handled = AtomicBoolean(false)
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {}
            }
            projection.registerCallback(callback, mainHandler)
            val vd = projection.createVirtualDisplay(
                "EasyCaptureSnap",
                targetW,
                targetH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            val finishSnap = {
                runCatching { reader.setOnImageAvailableListener(null, null) }
                runCatching { vd?.release() }
                runCatching { reader.close() }
                runCatching { projection.unregisterCallback(callback) }
                runCatching { projection.stop() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                QuickControlNotificationManager.restoreAfterRecording(this@ScreenCaptureService)
                stopSelf()
            }

            reader.setOnImageAvailableListener({ r ->
                if (!handled.compareAndSet(false, true)) return@setOnImageAvailableListener
                val image = runCatching { r.acquireLatestImage() }.getOrNull()
                if (image == null) {
                    finishSnap()
                    return@setOnImageAvailableListener
                }
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * targetW
                val paddedBitmap = Bitmap.createBitmap(
                    targetW + rowPadding / pixelStride,
                    targetH,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val cleanBitmap = if (paddedBitmap.width != targetW) {
                    Bitmap.createBitmap(paddedBitmap, 0, 0, targetW, targetH).also { paddedBitmap.recycle() }
                } else {
                    paddedBitmap
                }

                serviceScope.launch {
                    try {
                        val saved = MediaStorageHelper.saveScreenshotToDeviceStorage(
                            context = this@ScreenCaptureService,
                            bitmap = cleanBitmap
                        )
                        CaptureStateHolder.recordScreenshotSaved(saved.fileName, saved.displayDirectory)
                        if (hapticEnabled) performSubtleHaptic()
                    } catch (e: Exception) {
                        Log.e(TAG, "Standalone 1080p screenshot failed", e)
                    } finally {
                        cleanBitmap.recycle()
                        mainHandler.post { finishSnap() }
                    }
                }
            }, mainHandler)

            mainHandler.postDelayed({
                if (handled.compareAndSet(false, true)) {
                    finishSnap()
                }
            }, 2500L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed standalone screenshot", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            QuickControlNotificationManager.restoreAfterRecording(this)
            stopSelf()
        }
    }

    private fun applySystemShowTouches(enable: Boolean) {
        runCatching {
            val current = Settings.System.getInt(contentResolver, "show_touches", 0)
            if (enable) {
                previousSystemShowTouches = current
                if (current != 1) {
                    Settings.System.putInt(contentResolver, "show_touches", 1)
                }
            } else {
                previousSystemShowTouches = null
            }
        }
    }

    private fun restoreSystemShowTouches() {
        val prev = previousSystemShowTouches ?: return
        previousSystemShowTouches = null
        runCatching {
            Settings.System.putInt(contentResolver, "show_touches", prev)
        }
    }

    private fun drainVideoEncoder(endOfStream: Boolean) {
        synchronized(videoDrainLock) {
            val codec = videoEncoder ?: return
            val muxer = muxerCoordinator ?: return
            var attempts = 0
            while (attempts < 40) {
                attempts++
                val outputIndex = try {
                    codec.dequeueOutputBuffer(videoBufferInfo, if (endOfStream) 12_000L else 0L)
                } catch (_: Exception) {
                    break
                }
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) break
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxer.addVideoTrack(codec.outputFormat)
                    }
                    outputIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outputIndex)
                        if (encoded != null && videoBufferInfo.size > 0) {
                            encoded.position(videoBufferInfo.offset)
                            encoded.limit(videoBufferInfo.offset + videoBufferInfo.size)
                            muxer.writeVideoSample(encoded, videoBufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }
            }
        }
    }

    private data class EncoderConfigResult(
        val codec: MediaCodec,
        val format: VideoExportFormat,
        val width: Int,
        val height: Int
    )

    private fun createVideoEncoder(
        requestedFormat: VideoExportFormat,
        desiredWidth: Int,
        desiredHeight: Int,
        fps: Int,
        bitrateBps: Int
    ): EncoderConfigResult {
        val candidates = if (requestedFormat == VideoExportFormat.MP4_H264) {
            listOf(VideoExportFormat.MP4_H264)
        } else {
            listOf(requestedFormat, VideoExportFormat.MP4_H264)
        }

        // Uniform scale steps preserve 100% exact aspect ratio if a device codec rejects full resolution
        val scaleSteps = listOf(1.0f, 0.85f, 0.7f, 0.5f)
        var lastError: Throwable? = null

        for (candidate in candidates) {
            for (scale in scaleSteps) {
                val w = alignEven((desiredWidth * scale).roundToInt(), align = if (scale < 1f) 16 else 2)
                val h = alignEven((desiredHeight * scale).roundToInt(), align = if (scale < 1f) 16 else 2)
                var codec: MediaCodec? = null
                try {
                    val format = MediaFormat.createVideoFormat(candidate.videoMimeType, w, h).apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }
                    codec = MediaCodec.createEncoderByType(candidate.videoMimeType)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    return EncoderConfigResult(codec, candidate, w, h)
                } catch (t: Throwable) {
                    runCatching { codec?.release() }
                    lastError = t
                    Log.w(TAG, "Encoder ${candidate.shortLabel} at ${w}x${h} unavailable, trying next", t)
                }
            }
        }
        throw lastError ?: IllegalStateException("No compatible HD video encoder found")
    }

    private fun readDisplayMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use maximumWindowMetrics so we always capture the exact full physical screen bounds
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), density)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    /**
     * Computes encode dimensions while strictly preserving aspect ratio (zero squishing/compression):
     * - Full Screen: matches exact native screen size (or scales uniformly if a lower resolution was chosen).
     * - Custom / Preset Area: scales both axes uniformly to the selected HD tier without independent clamping.
     */
    private fun computeEncodeDimensions(
        displayW: Int,
        displayH: Int,
        crop: NormalizedRect,
        targetRes: VideoResolution
    ): Pair<Int, Int> {
        if (crop.isFullScreen()) {
            val nativeShort = minOf(displayW, displayH).coerceAtLeast(160)
            // When full screen is selected, record at exact screen size unless user explicitly picked a smaller tier
            val scale = if (targetRes.shortEdge < nativeShort) {
                targetRes.shortEdge.toFloat() / nativeShort.toFloat()
            } else {
                1.0f
            }
            val w = alignEven((displayW * scale).roundToInt(), align = 2)
            val h = alignEven((displayH * scale).roundToInt(), align = 2)
            return w to h
        }

        val regionW = (displayW * crop.width).coerceAtLeast(160f)
        val regionH = (displayH * crop.height).coerceAtLeast(160f)
        val regionShort = minOf(regionW, regionH)
        val scale = targetRes.shortEdge.toFloat() / regionShort
        var scaledW = regionW * scale
        var scaledH = regionH * scale

        // If extreme aspect ratio exceeds safe codec ceiling, scale BOTH axes uniformly (never clamp one axis)
        val maxCodecEdge = maxOf(targetRes.longEdge, 3840).toFloat()
        val longest = maxOf(scaledW, scaledH)
        if (longest > maxCodecEdge) {
            val uniformDown = maxCodecEdge / longest
            scaledW *= uniformDown
            scaledH *= uniformDown
        }

        val w = alignEven(scaledW.roundToInt(), align = 2)
        val h = alignEven(scaledH.roundToInt(), align = 2)
        return w to h
    }

    private fun alignEven(value: Int, align: Int): Int {
        val safeAlign = align.coerceAtLeast(2)
        return ((value + safeAlign / 2) / safeAlign).coerceAtLeast(16) * safeAlign
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && isRecording.get()) {
                val total = if (isPaused) {
                    elapsedAccumulatedMs
                } else {
                    elapsedAccumulatedMs + (System.currentTimeMillis() - resumeEpochMs).coerceAtLeast(0L)
                }
                CaptureStateHolder.updateElapsed(total)
                delay(100L)
            }
        }
    }

    private fun performSubtleHaptic() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        }
    }

    private fun cleanupCoreMedia() {
        restoreSystemShowTouches()
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { glPipeline?.release() }
        glPipeline = null

        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null

        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun cleanupResources() {
        floatingShutter?.dismiss()
        floatingShutter = null
        audioEngine?.stop()
        audioEngine = null
        muxerCoordinator?.stopAndRelease()
        muxerCoordinator = null
        cleanupCoreMedia()
        runCatching { tempOutputFile?.delete() }
        tempOutputFile = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snapIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_SCREENSHOT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isPaused) "Easy Capture (Paused)" else getString(R.string.notif_recording_title)
        val pauseLabel = if (isPaused) getString(R.string.notif_action_resume) else getString(R.string.notif_action_pause)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_recording_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, pauseLabel, pauseResumeIntent)
            .addAction(0, getString(R.string.notif_action_screenshot), snapIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    private fun updateNotification(isPaused: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    override fun onDestroy() {
        cleanupResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "easy_capture_channel"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_START = "com.example.capture.ACTION_START"
        const val ACTION_STOP = "com.example.capture.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.capture.ACTION_TOGGLE_PAUSE"
        const val ACTION_SCREENSHOT = "com.example.capture.ACTION_SCREENSHOT"
        const val ACTION_SINGLE_SCREENSHOT = "com.example.capture.ACTION_SINGLE_SCREENSHOT"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE_MBPS = "extra_bitrate_mbps"
        const val EXTRA_AUDIO_MODE = "extra_audio_mode"
        const val EXTRA_CROP_LEFT = "extra_crop_left"
        const val EXTRA_CROP_TOP = "extra_crop_top"
        const val EXTRA_CROP_RIGHT = "extra_crop_right"
        const val EXTRA_CROP_BOTTOM = "extra_crop_bottom"
        const val EXTRA_FLOATING_ENABLED = "extra_floating_enabled"
        const val EXTRA_FLOATING_OPACITY = "extra_floating_opacity"
        const val EXTRA_HAPTIC_ENABLED = "extra_haptic_enabled"
        const val EXTRA_SHOW_SCREEN_TOUCHES = "extra_show_screen_touches"
    }
}
