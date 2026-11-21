package com.example.editor

import android.graphics.Bitmap
import android.net.Uri
import com.example.data.NormalizedRect
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution

enum class EditorCropPreset(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_1_1("1:1", 1f)
}

data class LoadedVideoMetadata(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val thumbnails: List<Bitmap>
) {
    val effectiveWidth: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
    val effectiveHeight: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
}

data class VideoEditConfig(
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    val cropPreset: EditorCropPreset = EditorCropPreset.ORIGINAL,
    val cropRect: NormalizedRect = NormalizedRect.FULL,
    val speed: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val muteAudio: Boolean = false,
    val exportResolution: VideoResolution = VideoResolution.FHD_1080P,
    val exportFormat: VideoExportFormat = VideoExportFormat.MP4_H264,
    val exportFps: Int = 60,
    val exportBitrateMbps: Int = 16
)

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Exporting(val progress: Float, val stage: String) : ExportStatus
    data class Success(val fileName: String, val directory: String) : ExportStatus
    data class Error(val message: String) : ExportStatus
}

data class VideoEditorUiState(
    val isLoadingVideo: Boolean = false,
    val video: LoadedVideoMetadata? = null,
    val config: VideoEditConfig = VideoEditConfig(),
    val exportStatus: ExportStatus = ExportStatus.Idle,
    val loadError: String? = null
)
