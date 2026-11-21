package com.example.data

import android.media.MediaFormat
import android.media.MediaMuxer

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class VideoResolution(
    val shortLabel: String,
    val title: String,
    val shortEdge: Int,
    val longEdge: Int
) {
    HD_720P("720p", "HD 720p", 720, 1280),
    FHD_1080P("1080p", "Full HD 1080p", 1080, 1920),
    QHD_1440P("1440p", "2K QHD 1440p", 1440, 2560),
    UHD_4K("4K", "Ultra HD 4K", 2160, 3840)
}

enum class VideoExportFormat(
    val shortLabel: String,
    val title: String,
    val videoMimeType: String,
    val fileExtension: String,
    val contentMimeType: String,
    val muxerFormat: Int,
    val supportsAacAudio: Boolean
) {
    MP4_H264(
        shortLabel = "MP4 H.264",
        title = "MP4 (H.264 AVC)",
        videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
        fileExtension = "mp4",
        contentMimeType = "video/mp4",
        muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        supportsAacAudio = true
    ),
    MP4_HEVC(
        shortLabel = "MP4 HEVC",
        title = "MP4 (H.265 HEVC)",
        videoMimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
        fileExtension = "mp4",
        contentMimeType = "video/mp4",
        muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        supportsAacAudio = true
    ),
    WEBM_VP8(
        shortLabel = "WebM VP8",
        title = "WebM (VP8 HD)",
        videoMimeType = MediaFormat.MIMETYPE_VIDEO_VP8,
        fileExtension = "webm",
        contentMimeType = "video/webm",
        muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
        supportsAacAudio = false
    )
}

enum class AudioSourceMode(val label: String, val subtitle: String) {
    MICROPHONE("Mic", "Voice & ambient sound"),
    INTERNAL("Internal", "Device playback audio"),
    MIC_AND_INTERNAL("Mic + App", "Mixed real-time audio"),
    MUTE("Muted", "Video track only")
}

enum class RecordingAreaMode(val label: String, val aspectRatio: Float?) {
    FULL_SCREEN("Full Screen", null),
    RATIO_16_9("16:9 Wide", 16f / 9f),
    RATIO_9_16("9:16 Vertical", 9f / 16f),
    RATIO_4_3("4:3 Classic", 4f / 3f),
    RATIO_1_1("1:1 Square", 1f),
    CUSTOM("Custom Area", null)
}

data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceIn(0.15f, 1f)
    val height: Float get() = (bottom - top).coerceIn(0.15f, 1f)

    fun sanitized(): NormalizedRect {
        val l = left.coerceIn(0f, 0.85f)
        val t = top.coerceIn(0f, 0.85f)
        val r = right.coerceIn(l + 0.15f, 1f)
        val b = bottom.coerceIn(t + 0.15f, 1f)
        return NormalizedRect(l, t, r, b)
    }

    fun isFullScreen(): Boolean {
        return left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
    }

    companion object {
        val FULL = NormalizedRect(0f, 0f, 1f, 1f)

        fun forAspectRatio(targetRatio: Float, containerRatio: Float): NormalizedRect {
            if (targetRatio <= 0f || containerRatio <= 0f) return FULL
            return if (targetRatio > containerRatio) {
                val h = (containerRatio / targetRatio).coerceIn(0.2f, 1f)
                val top = (1f - h) / 2f
                NormalizedRect(0f, top, 1f, top + h)
            } else {
                val w = (targetRatio / containerRatio).coerceIn(0.2f, 1f)
                val left = (1f - w) / 2f
                NormalizedRect(left, 0f, left + w, 1f)
            }
        }
    }
}

data class CaptureSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val exportFormat: VideoExportFormat = VideoExportFormat.MP4_H264,
    val frameRate: Int = 60,
    val bitrateMbps: Int = 16,
    val audioSource: AudioSourceMode = AudioSourceMode.MICROPHONE,
    val areaMode: RecordingAreaMode = RecordingAreaMode.FULL_SCREEN,
    val customArea: NormalizedRect = NormalizedRect.FULL,
    val countdownSeconds: Int = 3,
    val floatingButtonEnabled: Boolean = true,
    val floatingButtonOpacity: Float = 0.82f,
    val hapticFeedbackEnabled: Boolean = true,
    val showScreenTouches: Boolean = false,
    val quickControlBarEnabled: Boolean = true
)

enum class RecordingPhase {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED
}

data class CaptureSessionState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val countdownRemaining: Int = 0,
    val elapsedMs: Long = 0L,
    val liveAudioLevel: Float = 0f,
    val screenshotCount: Int = 0,
    val lastSavedFileName: String? = null,
    val lastSavedDirectory: String? = null,
    val lastSavedType: String? = null,
    val errorMessage: String? = null
)
