package com.example.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.data.AudioSourceMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Real-time audio capture engine supporting:
 * - Microphone (MIC)
 * - Internal device audio (AudioPlaybackCaptureConfiguration on Android 10+)
 * - Real-time mixed Microphone + Internal audio
 * Encodes PCM directly to AAC and feeds the shared MuxerCoordinator while publishing live RMS audio levels.
 */
class RealtimeAudioEngine(
    private val mode: AudioSourceMode,
    private val mediaProjection: MediaProjection?,
    private val muxer: MuxerCoordinator,
    private val onLevelUpdated: (Float) -> Unit
) {
    private val running = AtomicBoolean(false)
    @Volatile
    var isPaused: Boolean = false

    private var workerThread: Thread? = null
    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null

    private var startTimeNs = -1L
    private var totalPausedNs = 0L
    private var pauseStartNs = 0L

    fun setPausedState(paused: Boolean) {
        if (paused && !isPaused) {
            isPaused = true
            if (startTimeNs >= 0L) {
                pauseStartNs = System.nanoTime()
            }
        } else if (!paused && isPaused) {
            if (startTimeNs >= 0L && pauseStartNs > 0L) {
                totalPausedNs += (System.nanoTime() - pauseStartNs).coerceAtLeast(0L)
            }
            pauseStartNs = 0L
            isPaused = false
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (mode == AudioSourceMode.MUTE) return false
        return try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING)
                .coerceAtLeast(SAMPLES_PER_FRAME * 2)
            val bufferSize = minBuf * 2

            if (mode == AudioSourceMode.MICROPHONE || mode == AudioSourceMode.MIC_AND_INTERNAL) {
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING,
                    bufferSize
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            }

            if ((mode == AudioSourceMode.INTERNAL || mode == AudioSourceMode.MIC_AND_INTERNAL) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                mediaProjection != null
            ) {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AUDIO_ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()

                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                    .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            }

            if (micRecord == null && internalRecord == null) {
                Log.w(TAG, "No AudioRecord source initialized for mode $mode")
                muxer.disableAudioTrackFallback()
                return false
            }

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLE_RATE,
                CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            micRecord?.startRecording()
            internalRecord?.startRecording()

            running.set(true)
            workerThread = Thread({ captureLoop() }, "EasyCapture-Audio").apply { start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RealtimeAudioEngine", e)
            stop()
            muxer.disableAudioTrackFallback()
            false
        }
    }

    private fun captureLoop() {
        val micShorts = ShortArray(SAMPLES_PER_FRAME)
        val internalShorts = ShortArray(SAMPLES_PER_FRAME)
        val mixedBytes = ByteArray(SAMPLES_PER_FRAME * 2)
        val bufferInfo = MediaCodec.BufferInfo()
        val codec = encoder ?: return

        while (running.get()) {
            val micRead = micRecord?.read(micShorts, 0, micShorts.size) ?: 0
            val internalRead = internalRecord?.read(internalShorts, 0, internalShorts.size) ?: 0
            val sampleCount = maxOf(micRead, internalRead)

            if (sampleCount <= 0) {
                continue
            }

            if (isPaused) {
                onLevelUpdated(0f)
                continue
            }

            var sumSquares = 0.0
            for (i in 0 until sampleCount) {
                val m = if (i < micRead) micShorts[i].toInt() else 0
                val sys = if (i < internalRead) internalShorts[i].toInt() else 0
                val mixed = (m + sys).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                sumSquares += (mixed * mixed).toDouble()
                mixedBytes[i * 2] = (mixed and 0xFF).toByte()
                mixedBytes[i * 2 + 1] = ((mixed ushr 8) and 0xFF).toByte()
            }

            val rms = sqrt(sumSquares / sampleCount) / Short.MAX_VALUE.toDouble()
            onLevelUpdated((rms * 4.2).toFloat().coerceIn(0f, 1f))

            val nowNs = System.nanoTime()
            if (startTimeNs < 0L) startTimeNs = nowNs
            val ptsUs = ((nowNs - startTimeNs - totalPausedNs).coerceAtLeast(0L)) / 1_000L

            try {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputIndex)
                    inputBuf?.clear()
                    val bytesToWrite = minOf(sampleCount * 2, inputBuf?.remaining() ?: 0)
                    inputBuf?.put(mixedBytes, 0, bytesToWrite)
                    codec.queueInputBuffer(inputIndex, 0, bytesToWrite, ptsUs, 0)
                }
                drainEncoder(codec, bufferInfo, endOfStream = false)
            } catch (e: Exception) {
                Log.w(TAG, "Audio encode step error: ${e.message}")
            }
        }

        // Send EOS
        try {
            val inputIndex = codec.dequeueInputBuffer(20_000L)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainEncoder(codec, bufferInfo, endOfStream = true)
        } catch (e: Exception) {
            Log.w(TAG, "Audio EOS drain error: ${e.message}")
        }
        onLevelUpdated(0f)
    }

    private fun drainEncoder(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo, endOfStream: Boolean) {
        var attempts = 0
        while (attempts < 20) {
            attempts++
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxer.addAudioTrack(codec.outputFormat)
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeAudioSample(encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            workerThread?.join(1200L)
        } catch (_: InterruptedException) {
        }
        workerThread = null

        runCatching { micRecord?.stop() }
        runCatching { micRecord?.release() }
        micRecord = null

        runCatching { internalRecord?.stop() }
        runCatching { internalRecord?.release() }
        internalRecord = null

        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
    }

    companion object {
        private const val TAG = "RealtimeAudioEngine"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 1
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BITRATE = 128_000
        private const val SAMPLES_PER_FRAME = 1024
    }
}
