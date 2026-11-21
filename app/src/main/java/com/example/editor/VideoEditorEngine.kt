package com.example.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.provider.OpenableColumns
import android.util.Log
import android.view.Surface
import com.example.data.MediaStorageHelper
import com.example.data.NormalizedRect
import com.example.data.SavedMediaResult
import com.example.data.VideoExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

class VideoEditorEngine {

    suspend fun inspectVideo(context: Context, uri: Uri): LoadedVideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(100L) ?: 1_000L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()?.coerceAtLeast(160) ?: 1080
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()?.coerceAtLeast(160) ?: 1920
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            val displayName = queryDisplayName(context, uri) ?: "Selected Video"

            val thumbs = mutableListOf<Bitmap>()
            val count = 8
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                val fraction = i.toFloat() / (count - 1).coerceAtLeast(1)
                val timeUs = (durationMs * 1000L * fraction).toLong()
                val raw = runCatching {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull()
                if (raw != null) {
                    val thumb = Bitmap.createScaledBitmap(raw, 120, 80, true)
                    if (thumb !== raw) raw.recycle()
                    thumbs.add(thumb)
                }
            }

            LoadedVideoMetadata(
                uri = uri,
                displayName = displayName,
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                thumbnails = thumbs
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun exportEditedVideo(
        context: Context,
        video: LoadedVideoMetadata,
        config: VideoEditConfig,
        onProgress: (Float, String) -> Unit
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val trimStartUs = (video.durationMs * 1000L * config.trimStartFraction).toLong().coerceAtLeast(0L)
        val trimEndUs = (video.durationMs * 1000L * config.trimEndFraction)
            .toLong()
            .coerceAtLeast(trimStartUs + 200_000L)
            .coerceAtMost(video.durationMs * 1000L)

        val needsTranscode = !config.cropRect.isFullScreen() ||
            abs(config.speed - 1.0f) > 0.01f ||
            config.exportFormat != VideoExportFormat.MP4_H264 ||
            config.rotationDegrees != 0 ||
            matchesResolution(video, config).not()

        onProgress(0.05f, "Preparing HD export pipeline…")

        val resolvedFormat: VideoExportFormat
        val tempFile: File

        if (!needsTranscode) {
            resolvedFormat = VideoExportFormat.MP4_H264
            tempFile = File(context.cacheDir, "ec_export_${System.currentTimeMillis()}.mp4")
            try {
                fastRemuxTrim(
                    context = context,
                    sourceUri = video.uri,
                    outputFile = tempFile,
                    trimStartUs = trimStartUs,
                    trimEndUs = trimEndUs,
                    muteAudio = config.muteAudio,
                    orientationDegrees = (video.rotationDegrees + config.rotationDegrees) % 360,
                    onProgress = { p -> onProgress(0.08f + p * 0.82f, "Trimming video stream…") }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fast remux failed, falling back to GPU HD transcode", e)
                runCatching { tempFile.delete() }
                val (file, fmt) = gpuTranscode(context, video, config, trimStartUs, trimEndUs, onProgress)
                return@withContext finalizeExport(context, file, fmt, onProgress)
            }
        } else {
            val (file, fmt) = gpuTranscode(context, video, config, trimStartUs, trimEndUs, onProgress)
            tempFile = file
            resolvedFormat = fmt
        }

        finalizeExport(context, tempFile, resolvedFormat, onProgress)
    }

    private fun finalizeExport(
        context: Context,
        tempFile: File,
        format: VideoExportFormat,
        onProgress: (Float, String) -> Unit
    ): SavedMediaResult {
        onProgress(0.94f, "Saving to Movies/EasyCapture…")
        return try {
            val result = MediaStorageHelper.publishVideoToDeviceStorage(context, tempFile, format)
            onProgress(1f, "Saved ${result.fileName}")
            result
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun matchesResolution(video: LoadedVideoMetadata, config: VideoEditConfig): Boolean {
        val shortEdge = minOf(video.effectiveWidth, video.effectiveHeight)
        return abs(shortEdge - config.exportResolution.shortEdge) <= 32
    }

    private fun fastRemuxTrim(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        trimStartUs: Long,
        trimEndUs: Long,
        muteAudio: Boolean,
        orientationDegrees: Int,
        onProgress: (Float) -> Unit
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, sourceUri, null)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(orientationDegrees)

            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || (!muteAudio && mime.startsWith("audio/"))) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) throw IllegalStateException("No playable tracks found in video")

            muxer.start()
            if (trimStartUs > 0L) {
                extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var basePtsUs = -1L
            val spanUs = (trimEndUs - trimStartUs).coerceAtLeast(1L)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > trimEndUs) break

                val srcTrack = extractor.sampleTrackIndex
                val dstTrack = trackMap[srcTrack]
                if (dstTrack != null && sampleTimeUs >= trimStartUs) {
                    if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(dstTrack, buffer, info)
                    onProgress(((sampleTimeUs - trimStartUs).toFloat() / spanUs).coerceIn(0f, 1f))
                }
                extractor.advance()
            }
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private suspend fun gpuTranscode(
        context: Context,
        video: LoadedVideoMetadata,
        config: VideoEditConfig,
        trimStartUs: Long,
        trimEndUs: Long,
        onProgress: (Float, String) -> Unit
    ): Pair<File, VideoExportFormat> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var glContext: TranscodeGlSurface? = null

        val targetResolution = config.exportResolution
        val crop = config.cropRect.sanitized()
        val totalRotation = (video.rotationDegrees + config.rotationDegrees) % 360
        val rotated = totalRotation == 90 || totalRotation == 270
        val displayW = if (rotated) video.height else video.width
        val displayH = if (rotated) video.width else video.height
        val croppedW = (displayW * crop.width).coerceAtLeast(160f)
        val croppedH = (displayH * crop.height).coerceAtLeast(160f)
        val croppedShort = minOf(croppedW, croppedH)
        val scale = targetResolution.shortEdge.toFloat() / croppedShort
        var scaledW = croppedW * scale
        var scaledH = croppedH * scale
        val maxEdge = maxOf(targetResolution.longEdge, 3840).toFloat()
        val longest = maxOf(scaledW, scaledH)
        if (longest > maxEdge) {
            val down = maxEdge / longest
            scaledW *= down
            scaledH *= down
        }
        val outW = ((scaledW.roundToInt() + 1) / 2).coerceAtLeast(16) * 2
        val outH = ((scaledH.roundToInt() + 1) / 2).coerceAtLeast(16) * 2

        try {
            extractor.setDataSource(context, video.uri, null)
            var videoTrack = -1
            var audioTrack = -1
            var videoInputFormat: MediaFormat? = null
            var audioInputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (videoTrack == -1 && mime.startsWith("video/")) {
                    videoTrack = i
                    videoInputFormat = fmt
                } else if (audioTrack == -1 && mime.startsWith("audio/")) {
                    audioTrack = i
                    audioInputFormat = fmt
                }
            }
            if (videoTrack == -1 || videoInputFormat == null) {
                throw IllegalStateException("Selected file does not contain a valid video stream")
            }

            val (createdEncoder, resolvedFormat) = createExportEncoder(
                requested = config.exportFormat,
                width = outW,
                height = outH,
                fps = config.exportFps,
                bitrateBps = config.exportBitrateMbps * 1_000_000
            )
            encoder = createdEncoder

            val tempFile = File(
                context.cacheDir,
                "ec_hd_${System.currentTimeMillis()}.${resolvedFormat.fileExtension}"
            )
            muxer = MediaMuxer(tempFile.absolutePath, resolvedFormat.muxerFormat)

            val encoderSurface = encoder.createInputSurface()
            encoder.start()

            glContext = TranscodeGlSurface(
                encoderSurface = encoderSurface,
                outWidth = outW,
                outHeight = outH,
                cropRect = crop,
                extraRotationDegrees = config.rotationDegrees
            )

            val decoderMime = videoInputFormat.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(decoderMime).apply {
                configure(videoInputFormat, glContext.decoderInputSurface, null, 0)
                start()
            }

            extractor.selectTrack(videoTrack)
            if (trimStartUs > 0L) {
                extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val copyAudio = !config.muteAudio &&
                audioTrack != -1 &&
                audioInputFormat != null &&
                resolvedFormat.supportsAacAudio &&
                abs(config.speed - 1.0f) < 0.01f

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var firstFramePtsUs = -1L
            var lastWrittenVideoPtsUs = -1L
            val spanUs = (trimEndUs - trimStartUs).coerceAtLeast(1L)

            onProgress(0.12f, "Encoding ${targetResolution.shortLabel} ${resolvedFormat.shortLabel}…")

            while (!encoderDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTime = extractor.sampleTime
                        if (sampleSize < 0 || sampleTime > trimEndUs) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, 10_000L)
                    if (outIdx >= 0) {
                        val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val shouldRender = decInfo.size > 0 && decInfo.presentationTimeUs >= trimStartUs &&
                            decInfo.presentationTimeUs <= trimEndUs

                        decoder.releaseOutputBuffer(outIdx, shouldRender)
                        if (shouldRender) {
                            if (glContext.awaitNewFrame()) {
                                if (firstFramePtsUs < 0L) firstFramePtsUs = decInfo.presentationTimeUs
                                val relUs = (decInfo.presentationTimeUs - firstFramePtsUs).coerceAtLeast(0L)
                                val scaledPtsUs = (relUs / config.speed).toLong()
                                glContext.drawFrame(scaledPtsUs * 1_000L)
                                val pct = ((decInfo.presentationTimeUs - trimStartUs).toFloat() / spanUs).coerceIn(0f, 1f)
                                onProgress(0.12f + pct * 0.72f, "Encoding ${targetResolution.shortLabel} ${resolvedFormat.shortLabel}…")
                            }
                        }
                        if (isEos) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                // Drain encoder
                var draining = true
                while (draining) {
                    val encIdx = encoder.dequeueOutputBuffer(encInfo, if (decoderDone) 10_000L else 0L)
                    when {
                        encIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> draining = false
                        encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (copyAudio && audioInputFormat != null) {
                                muxerAudioTrack = muxer.addTrack(audioInputFormat)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                        encIdx >= 0 -> {
                            val encoded = encoder.getOutputBuffer(encIdx)
                            if (encoded != null && encInfo.size > 0 && muxerStarted &&
                                (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(encInfo.offset)
                                encoded.limit(encInfo.offset + encInfo.size)
                                val pts = if (encInfo.presentationTimeUs <= lastWrittenVideoPtsUs) {
                                    lastWrittenVideoPtsUs + 1_000L
                                } else {
                                    encInfo.presentationTimeUs
                                }
                                lastWrittenVideoPtsUs = pts
                                val writeInfo = MediaCodec.BufferInfo().apply {
                                    set(encInfo.offset, encInfo.size, pts, encInfo.flags)
                                }
                                muxer.writeSampleData(muxerVideoTrack, encoded, writeInfo)
                            }
                            encoder.releaseOutputBuffer(encIdx, false)
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                encoderDone = true
                                draining = false
                            }
                        }
                    }
                }
            }

            // Copy trimmed audio track cleanly if enabled
            if (copyAudio && muxerStarted && muxerAudioTrack != -1) {
                onProgress(0.86f, "Muxing audio track…")
                val audioExtractor = MediaExtractor()
                try {
                    audioExtractor.setDataSource(context, video.uri, null)
                    audioExtractor.selectTrack(audioTrack)
                    if (trimStartUs > 0L) {
                        audioExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    }
                    val audioBuf = ByteBuffer.allocate(256 * 1024)
                    val audioInfo = MediaCodec.BufferInfo()
                    var audioBasePts = -1L
                    var lastAudioPts = -1L
                    while (true) {
                        val size = audioExtractor.readSampleData(audioBuf, 0)
                        if (size < 0) break
                        val pts = audioExtractor.sampleTime
                        if (pts > trimEndUs) break
                        if (pts >= trimStartUs) {
                            if (audioBasePts < 0L) audioBasePts = pts
                            val normPts = (pts - audioBasePts).coerceAtLeast(0L)
                            val safePts = if (normPts <= lastAudioPts) lastAudioPts + 1_000L else normPts
                            lastAudioPts = safePts
                            audioInfo.set(0, size, safePts, 0)
                            muxer.writeSampleData(muxerAudioTrack, audioBuf, audioInfo)
                        }
                        audioExtractor.advance()
                    }
                } finally {
                    runCatching { audioExtractor.release() }
                }
            }

            return tempFile to resolvedFormat
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { glContext?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun createExportEncoder(
        requested: VideoExportFormat,
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int
    ): Pair<MediaCodec, VideoExportFormat> {
        val candidates = if (requested == VideoExportFormat.MP4_H264) {
            listOf(VideoExportFormat.MP4_H264)
        } else {
            listOf(requested, VideoExportFormat.MP4_H264)
        }
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val fmt = MediaFormat.createVideoFormat(candidate.videoMimeType, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                val codec = MediaCodec.createEncoderByType(candidate.videoMimeType)
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                return codec to candidate
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No compatible HD video encoder available")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    /**
     * Synchronous OpenGL ES 2.0 transcoding surface for cropping, rotating, and scaling decoded video frames.
     */
    private class TranscodeGlSurface(
        encoderSurface: Surface,
        private val outWidth: Int,
        private val outHeight: Int,
        cropRect: NormalizedRect,
        extraRotationDegrees: Int
    ) : SurfaceTexture.OnFrameAvailableListener {

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var programId = 0
        private var textureId = 0
        private val surfaceTexture: SurfaceTexture
        val decoderInputSurface: Surface

        private val frameSyncLock = Object()
        private var frameAvailable = false

        private val stMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16)
        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        private var aPosLoc = 0
        private var aTexLoc = 0
        private var uMvpLoc = 0
        private var uStLoc = 0

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, configs[0], encoderSurface, intArrayOf(EGL14.EGL_NONE), 0
            )
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            Matrix.setIdentityM(mvpMatrix, 0)
            if (extraRotationDegrees != 0) {
                Matrix.rotateM(mvpMatrix, 0, -extraRotationDegrees.toFloat(), 0f, 0f, 1f)
            }

            vertexBuffer = toFloatBuffer(
                floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            )
            val c = cropRect.sanitized()
            texCoordBuffer = toFloatBuffer(
                floatArrayOf(
                    c.left, 1f - c.bottom,
                    c.right, 1f - c.bottom,
                    c.left, 1f - c.top,
                    c.right, 1f - c.top
                )
            )

            programId = buildProgram()
            aPosLoc = GLES20.glGetAttribLocation(programId, "aPosition")
            aTexLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
            uMvpLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
            uStLoc = GLES20.glGetUniformLocation(programId, "uSTMatrix")

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(textureId).apply {
                setOnFrameAvailableListener(this@TranscodeGlSurface)
            }
            decoderInputSurface = Surface(surfaceTexture)
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSyncLock) {
                frameAvailable = true
                frameSyncLock.notifyAll()
            }
        }

        fun awaitNewFrame(timeoutMs: Long = 1500L): Boolean {
            synchronized(frameSyncLock) {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!frameAvailable) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) return false
                    try {
                        frameSyncLock.wait(remaining)
                    } catch (_: InterruptedException) {
                        return false
                    }
                }
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
            return true
        }

        fun drawFrame(ptsNs: Long) {
            surfaceTexture.getTransformMatrix(stMatrix)
            GLES20.glViewport(0, 0, outWidth, outHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uStLoc, 1, false, stMatrix, 0)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPosLoc)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aTexLoc)
            GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            runCatching { decoderInputSurface.release() }
            runCatching { surfaceTexture.release() }
            if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            if (programId != 0) GLES20.glDeleteProgram(programId)
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }

        private fun buildProgram(): Int {
            val vs = compile(
                GLES20.GL_VERTEX_SHADER,
                """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uMvpMatrix;
                uniform mat4 uSTMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = uMvpMatrix * aPosition;
                    vTexCoord = (uSTMatrix * aTexCoord).xy;
                }
                """.trimIndent()
            )
            val fs = compile(
                GLES20.GL_FRAGMENT_SHADER,
                """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTexCoord);
                }
                """.trimIndent()
            )
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)
            return program
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }

        private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(arr.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(arr)
                    position(0)
                }
    }

    companion object {
        private const val TAG = "VideoEditorEngine"
    }
}
