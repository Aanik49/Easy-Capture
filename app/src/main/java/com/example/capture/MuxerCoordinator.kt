package com.example.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

class MuxerCoordinator(
    outputPath: String,
    outputFormat: Int,
    private val expectAudioTrack: Boolean
) {
    private val lock = Any()
    private val muxer = MediaMuxer(outputPath, outputFormat)

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    @Volatile
    private var started = false
    private var released = false

    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L
    private var hasWrittenAnySample = false

    val isStarted: Boolean get() = started

    fun addVideoTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (released) return -1
            if (videoTrackIndex == -1) {
                videoTrackIndex = muxer.addTrack(format)
                tryStartLocked()
            }
            return videoTrackIndex
        }
    }

    fun addAudioTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (released) return -1
            if (audioTrackIndex == -1) {
                audioTrackIndex = muxer.addTrack(format)
                tryStartLocked()
            }
            return audioTrackIndex
        }
    }

    fun disableAudioTrackFallback() {
        synchronized(lock) {
            if (released) return
            if (!started && videoTrackIndex != -1) {
                tryStartLocked(forceWithoutAudio = true)
            }
        }
    }

    private fun tryStartLocked(forceWithoutAudio: Boolean = false) {
        if (started || released) return
        val audioReady = !expectAudioTrack || forceWithoutAudio || audioTrackIndex != -1
        if (videoTrackIndex != -1 && audioReady) {
            try {
                muxer.start()
                started = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MediaMuxer", e)
            }
        }
    }

    fun writeVideoSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!started || released || videoTrackIndex == -1 || info.size <= 0) return
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
            val adjustedInfo = MediaCodec.BufferInfo().apply {
                set(
                    info.offset,
                    info.size,
                    if (info.presentationTimeUs <= lastVideoPtsUs) lastVideoPtsUs + 1_000L else info.presentationTimeUs,
                    info.flags
                )
            }
            lastVideoPtsUs = adjustedInfo.presentationTimeUs
            try {
                muxer.writeSampleData(videoTrackIndex, buffer, adjustedInfo)
                hasWrittenAnySample = true
            } catch (e: Exception) {
                Log.w(TAG, "Error writing video sample", e)
            }
        }
    }

    fun writeAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!started || released || audioTrackIndex == -1 || info.size <= 0) return
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
            val adjustedInfo = MediaCodec.BufferInfo().apply {
                set(
                    info.offset,
                    info.size,
                    if (info.presentationTimeUs <= lastAudioPtsUs) lastAudioPtsUs + 1_000L else info.presentationTimeUs,
                    info.flags
                )
            }
            lastAudioPtsUs = adjustedInfo.presentationTimeUs
            try {
                muxer.writeSampleData(audioTrackIndex, buffer, adjustedInfo)
                hasWrittenAnySample = true
            } catch (e: Exception) {
                Log.w(TAG, "Error writing audio sample", e)
            }
        }
    }

    fun stopAndRelease(): Boolean {
        synchronized(lock) {
            if (released) return hasWrittenAnySample
            released = true
            if (started) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "MediaMuxer stop warning: ${e.message}")
                }
            }
            try {
                muxer.release()
            } catch (e: Exception) {
                Log.w(TAG, "MediaMuxer release warning: ${e.message}")
            }
            return hasWrittenAnySample
        }
    }

    companion object {
        private const val TAG = "MuxerCoordinator"
    }
}
