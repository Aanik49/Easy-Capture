package com.example.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.NormalizedRect
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution
import com.example.editor.EditorCropPreset
import com.example.editor.ExportStatus
import com.example.editor.LoadedVideoMetadata
import com.example.editor.VideoEditConfig
import com.example.editor.VideoEditorUiState
import com.example.ui.components.AutoSaveStatusBanner
import com.example.ui.components.StudioSectionHeader
import com.example.ui.components.formatShortTime
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    uiState: VideoEditorUiState,
    onPickVideoClick: () -> Unit,
    onUpdateTrim: (Float, Float) -> Unit,
    onSelectCropPreset: (EditorCropPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onRotate90: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFormat: (VideoExportFormat) -> Unit,
    onSelectFps: (Int) -> Unit,
    onExportClick: () -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState.isLoadingVideo) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "Reading video timeline…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.video == null) {
            EditorEmptyPickerState(
                errorMessage = uiState.loadError,
                onPickVideoClick = onPickVideoClick
            )
        } else {
            LoadedVideoEditorContent(
                video = uiState.video,
                config = uiState.config,
                exportStatus = uiState.exportStatus,
                onPickVideoClick = onPickVideoClick,
                onUpdateTrim = onUpdateTrim,
                onSelectCropPreset = onSelectCropPreset,
                onUpdateSpeed = onUpdateSpeed,
                onRotate90 = onRotate90,
                onToggleMute = onToggleMute,
                onSelectResolution = onSelectResolution,
                onSelectFormat = onSelectFormat,
                onSelectFps = onSelectFps,
                onExportClick = onExportClick,
                onDismissStatus = onDismissStatus
            )
        }
    }
}

@Composable
private fun EditorEmptyPickerState(
    errorMessage: String?,
    onPickVideoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.14f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.editor_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.editor_pick_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onPickVideoClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("pick_video_button")
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.editor_pick_video),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "Exports auto-save to Movies/EasyCapture/EasyCapture_<timestamp>",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedVideoEditorContent(
    video: LoadedVideoMetadata,
    config: VideoEditConfig,
    exportStatus: ExportStatus,
    onPickVideoClick: () -> Unit,
    onUpdateTrim: (Float, Float) -> Unit,
    onSelectCropPreset: (EditorCropPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onRotate90: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFormat: (VideoExportFormat) -> Unit,
    onSelectFps: (Int) -> Unit,
    onExportClick: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val isExporting = exportStatus is ExportStatus.Exporting
    val trimStartMs = (video.durationMs * config.trimStartFraction).toLong()
    val trimEndMs = (video.durationMs * config.trimEndFraction).toLong()
    val trimmedDurationMs = ((trimEndMs - trimStartMs).coerceAtLeast(100L) / config.speed).toLong()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Export Success / Error Banner
        item(key = "export_banner") {
            val success = exportStatus as? ExportStatus.Success
            val error = exportStatus as? ExportStatus.Error
            AutoSaveStatusBanner(
                fileName = success?.fileName,
                directory = success?.directory,
                mediaType = "Edited HD Video",
                errorMessage = error?.message,
                onDismiss = onDismissStatus
            )
        }

        // Video Preview Player + Interactive Crop Mask
        item(key = "preview_player") {
            VideoPreviewCard(
                video = video,
                config = config,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                onPickVideoClick = onPickVideoClick
            )
        }

        // Filmstrip Trim Timeline
        item(key = "trim_timeline") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioSectionHeader(
                        eyebrow = "Clip Range",
                        title = stringResource(R.string.editor_trim_section),
                        trailing = {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "OUT ${formatShortTime(trimmedDurationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    )

                    // Filmstrip Thumbnail Bar
                    if (video.thumbnails.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                video.thumbnails.forEach { bmp ->
                                    if (!bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                            // Dim trimmed-out ends
                            val cyan = MaterialTheme.colorScheme.secondary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val startX = size.width * config.trimStartFraction
                                val endX = size.width * config.trimEndFraction
                                drawRect(Color.Black.copy(alpha = 0.65f), Offset.Zero, Size(startX, size.height))
                                drawRect(Color.Black.copy(alpha = 0.65f), Offset(endX, 0f), Size(size.width - endX, size.height))
                                drawRect(
                                    color = cyan,
                                    topLeft = Offset(startX, 0f),
                                    size = Size((endX - startX).coerceAtLeast(2f), size.height),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }

                    RangeSlider(
                        value = config.trimStartFraction..config.trimEndFraction,
                        onValueChange = { range -> onUpdateTrim(range.start, range.endInclusive) },
                        enabled = !isExporting,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("editor_trim_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IN  ${formatShortTime(trimStartMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "OUT  ${formatShortTime(trimEndMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Crop, Speed, Rotation & Audio Adjustments
        item(key = "edit_tools") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioSectionHeader(
                        eyebrow = "Transform & Audio",
                        title = stringResource(R.string.editor_adjust_section)
                    )

                    // Crop Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditorCropPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.cropPreset == preset,
                                enabled = !isExporting,
                                onClick = { onSelectCropPreset(preset) },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelLarge) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("editor_crop_${preset.name.lowercase()}")
                            )
                        }
                    }

                    // Speed Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            FilterChip(
                                selected = config.speed == spd,
                                enabled = !isExporting,
                                onClick = { onUpdateSpeed(spd) },
                                label = { Text("${spd}x", style = MaterialTheme.typography.labelLarge) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("editor_speed_${(spd * 100).roundToInt()}")
                            )
                        }
                    }

                    // Rotate & Audio Mute Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRotate90,
                            enabled = !isExporting,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("editor_rotate_button")
                        ) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate ${config.rotationDegrees}°")
                        }

                        OutlinedButton(
                            onClick = onToggleMute,
                            enabled = !isExporting,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (config.muteAudio) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("editor_mute_button")
                        ) {
                            Icon(
                                imageVector = if (config.muteAudio) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = if (config.muteAudio) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (config.muteAudio) "Audio Muted" else "Keep Audio")
                        }
                    }
                }
            }
        }

        // High-Definition Multi-Format Export Deck
        item(key = "hd_export") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioSectionHeader(
                        eyebrow = "High-Definition Render",
                        title = stringResource(R.string.editor_export_section),
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoResolution.entries.forEach { res ->
                            FilterChip(
                                selected = config.exportResolution == res,
                                enabled = !isExporting,
                                onClick = { onSelectResolution(res) },
                                label = { Text(res.shortLabel, style = MaterialTheme.typography.labelLarge) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_res_${res.shortLabel.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoExportFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = config.exportFormat == fmt,
                                enabled = !isExporting,
                                onClick = { onSelectFormat(fmt) },
                                label = { Text(fmt.shortLabel, style = MaterialTheme.typography.labelLarge) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("export_fmt_${fmt.name.lowercase()}")
                            )
                        }

                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = config.exportFps == fps,
                                enabled = !isExporting,
                                onClick = { onSelectFps(fps) },
                                label = { Text("${fps} FPS", style = MaterialTheme.typography.labelLarge) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("export_fps_$fps")
                            )
                        }
                    }

                    if (exportStatus is ExportStatus.Exporting) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = exportStatus.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(exportStatus.progress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { exportStatus.progress.coerceIn(0f, 1f) },
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                        }
                    }

                    Button(
                        onClick = onExportClick,
                        enabled = !isExporting,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("export_video_button")
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExporting) {
                                stringResource(R.string.editor_exporting)
                            } else {
                                "${stringResource(R.string.editor_export_button)} (${config.exportResolution.shortLabel} ${config.exportFormat.shortLabel})"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun VideoPreviewCard(
    video: LoadedVideoMetadata,
    config: VideoEditConfig,
    trimStartMs: Long,
    trimEndMs: Long,
    onPickVideoClick: () -> Unit
) {
    var videoViewRef by remember(video.uri) { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var currentPosMs by remember(video.uri) { mutableLongStateOf(0L) }

    LaunchedEffect(video.uri, isPlaying, trimStartMs, trimEndMs) {
        while (isPlaying) {
            val vv = videoViewRef
            if (vv != null) {
                val pos = runCatching { vv.currentPosition.toLong() }.getOrDefault(0L)
                currentPosMs = pos
                if (pos >= trimEndMs) {
                    runCatching {
                        vv.seekTo(trimStartMs.toInt())
                        vv.start()
                    }
                }
            }
            delay(150L)
        }
    }

    DisposableEffect(video.uri) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    val cyan = MaterialTheme.colorScheme.secondary

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${video.effectiveWidth}×${video.effectiveHeight} · ${formatShortTime(video.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onPickVideoClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("change_video_button")
                ) {
                    Text(stringResource(R.string.editor_change_video), style = MaterialTheme.typography.labelLarge)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(video.uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                seekTo(trimStartMs.toInt())
                            }
                            videoViewRef = this
                        }
                    },
                    update = { view ->
                        if (videoViewRef !== view) {
                            videoViewRef = view
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = config.rotationDegrees.toFloat()
                        }
                )

                // Crop overlay guide when cropped
                if (!config.cropRect.isFullScreen()) {
                    CropOverlayGuide(cropRect = config.cropRect, accentColor = cyan)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val vv = videoViewRef ?: return@IconButton
                        if (isPlaying) {
                            runCatching { vv.pause() }
                            isPlaying = false
                        } else {
                            runCatching {
                                if (vv.currentPosition < trimStartMs || vv.currentPosition >= trimEndMs) {
                                    vv.seekTo(trimStartMs.toInt())
                                }
                                vv.start()
                            }
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .testTag("editor_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "${formatShortTime(currentPosMs)} / ${formatShortTime(video.durationMs)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${config.speed}x · ${config.cropPreset.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun CropOverlayGuide(cropRect: NormalizedRect, accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val c = cropRect.sanitized()
        val l = c.left * size.width
        val t = c.top * size.height
        val r = c.right * size.width
        val b = c.bottom * size.height
        val mask = Color.Black.copy(alpha = 0.55f)
        drawRect(mask, Offset.Zero, Size(size.width, t))
        drawRect(mask, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(mask, Offset(0f, t), Size(l, b - t))
        drawRect(mask, Offset(r, t), Size(size.width - r, b - t))
        drawRect(
            color = accentColor,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
