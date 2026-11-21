package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AudioSourceMode
import com.example.data.CaptureSessionState
import com.example.data.CaptureSettings
import com.example.data.NormalizedRect
import com.example.data.RecordingAreaMode
import com.example.data.RecordingPhase
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution
import com.example.ui.components.AutoSaveStatusBanner
import com.example.ui.components.BroadcastAudioMeter
import com.example.ui.components.CustomAreaInputDialog
import com.example.ui.components.InteractiveAreaFramePreview
import com.example.ui.components.StudioSectionHeader
import com.example.ui.components.StudioSwitch
import com.example.ui.components.formatDurationTimer
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun CaptureScreen(
    settings: CaptureSettings,
    captureState: CaptureSessionState,
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    onStartRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onTogglePauseClick: () -> Unit,
    onSnapScreenshotClick: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectAreaMode: (RecordingAreaMode, NormalizedRect) -> Unit,
    onSelectAudioMode: (AudioSourceMode) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFormat: (VideoExportFormat) -> Unit,
    onSelectFps: (Int) -> Unit,
    onToggleFloatingButton: (Boolean) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = captureState.phase == RecordingPhase.RECORDING || captureState.phase == RecordingPhase.PAUSED

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Auto-Save / Error feedback banner (no media gallery)
            item(key = "status_banner") {
                AutoSaveStatusBanner(
                    fileName = captureState.lastSavedFileName,
                    directory = captureState.lastSavedDirectory,
                    mediaType = captureState.lastSavedType,
                    errorMessage = captureState.errorMessage,
                    onDismiss = onDismissBanner
                )
            }

            // Broadcast Studio Hero Telemetry Card
            item(key = "hero_card") {
                StudioTelemetryHeroCard(
                    settings = settings,
                    captureState = captureState,
                    onStartRecordingClick = onStartRecordingClick,
                    onStopRecordingClick = onStopRecordingClick,
                    onTogglePauseClick = onTogglePauseClick,
                    onSnapScreenshotClick = onSnapScreenshotClick
                )
            }

            // Recording Area Selection Card
            item(key = "recording_area") {
                RecordingAreaCard(
                    settings = settings,
                    enabled = !isLive,
                    onSelectArea = onSelectAreaMode
                )
            }

            // Real-Time Audio Capture Card
            item(key = "audio_capture") {
                AudioCaptureCard(
                    selectedMode = settings.audioSource,
                    hasMicPermission = hasMicPermission,
                    enabled = !isLive,
                    onSelectMode = onSelectAudioMode
                )
            }

            // High-Definition Format & Quality Card
            item(key = "hd_output") {
                HdOutputProfileCard(
                    settings = settings,
                    enabled = !isLive,
                    onSelectResolution = onSelectResolution,
                    onSelectFormat = onSelectFormat,
                    onSelectFps = onSelectFps
                )
            }

            // Stealth Floating 1080p Screenshot Button Card
            item(key = "floating_shutter") {
                FloatingShutterSetupCard(
                    enabled = settings.floatingButtonEnabled,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleEnabled = onToggleFloatingButton,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }

            // Storage Auto-Save Footer Note
            item(key = "storage_footer") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.storage_auto_save_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Filename prefix: EasyCapture_<timestamp>",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StudioTelemetryHeroCard(
    settings: CaptureSettings,
    captureState: CaptureSessionState,
    onStartRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onTogglePauseClick: () -> Unit,
    onSnapScreenshotClick: () -> Unit
) {
    val isRecording = captureState.phase == RecordingPhase.RECORDING
    val isPaused = captureState.phase == RecordingPhase.PAUSED
    val isActive = isRecording || isPaused

    val pulseTransition = rememberInfiniteTransition(label = "tally_pulse")
    val tallyAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tally_alpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when (captureState.phase) {
            RecordingPhase.RECORDING -> MaterialTheme.colorScheme.primary
            RecordingPhase.PAUSED -> MaterialTheme.colorScheme.tertiary
            RecordingPhase.COUNTDOWN -> MaterialTheme.colorScheme.tertiary
            RecordingPhase.IDLE -> MaterialTheme.colorScheme.secondary
        },
        label = "status_color"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0D1117),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isRecording) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_banner),
                contentDescription = "Broadcast capture studio artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.36f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0D1117),
                                Color(0xE60B0D11)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Pill & Spec Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .alpha(if (isRecording) tallyAlpha else 1f)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = when (captureState.phase) {
                                    RecordingPhase.RECORDING -> stringResource(R.string.capture_recording)
                                    RecordingPhase.PAUSED -> stringResource(R.string.capture_paused)
                                    RecordingPhase.COUNTDOWN -> "${stringResource(R.string.capture_countdown)} ${captureState.countdownRemaining}S"
                                    RecordingPhase.IDLE -> stringResource(R.string.capture_ready)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = "${settings.resolution.shortLabel} · ${settings.frameRate}FPS · ${settings.exportFormat.shortLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9BA7BC)
                    )
                }

                // Precision Timer + Live Screenshot Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = formatDurationTimer(captureState.elapsedMs),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.testTag("capture_timer_text")
                        )
                        Text(
                            text = "${settings.areaMode.label}  •  ${settings.audioSource.label} Audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAB6CA)
                        )
                    }

                    if (isActive || captureState.screenshotCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF17212C),
                            border = BorderStroke(1.dp, Color(0xFF283A4E))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "1080p screenshots taken",
                                    tint = Color(0xFF00D4B4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${captureState.screenshotCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Real-time Audio Level Meter
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AUDIO INPUT LEVEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8A97AD)
                        )
                        Text(
                            text = if (settings.audioSource == AudioSourceMode.MUTE) "MUTED" else settings.audioSource.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRecording && settings.audioSource != AudioSourceMode.MUTE) Color(0xFF00D4B4) else Color(0xFF8A97AD)
                        )
                    }
                    BroadcastAudioMeter(
                        level = captureState.liveAudioLevel,
                        isActive = isRecording && settings.audioSource != AudioSourceMode.MUTE
                    )
                }

                // Primary Broadcast Controls
                if (!isActive) {
                    Button(
                        onClick = onStartRecordingClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_start_recording),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStopRecordingClick,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(54.dp)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_stop_recording),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onTogglePauseClick,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF3A465C)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("pause_recording_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPaused) stringResource(R.string.action_resume_recording) else stringResource(R.string.action_pause_recording),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onSnapScreenshotClick,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF00D4B4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D4B4)),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("in_app_screenshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Capture 1080p screenshot"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingAreaCard(
    settings: CaptureSettings,
    enabled: Boolean,
    onSelectArea: (RecordingAreaMode, NormalizedRect) -> Unit
) {
    var showCustomAreaDialog by remember { mutableStateOf(false) }

    if (showCustomAreaDialog) {
        CustomAreaInputDialog(
            initialRect = settings.customArea,
            resolution = settings.resolution,
            onDismiss = { showCustomAreaDialog = false },
            onApply = { chosenRect ->
                showCustomAreaDialog = false
                onSelectArea(RecordingAreaMode.CUSTOM, chosenRect)
            }
        )
    }

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
                eyebrow = "Frame Framing",
                title = stringResource(R.string.section_recording_area),
                trailing = {
                    Icon(
                        imageVector = if (settings.areaMode == RecordingAreaMode.CUSTOM) Icons.Rounded.Crop else Icons.Rounded.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecordingAreaMode.entries.forEach { mode ->
                    val selected = settings.areaMode == mode
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        onClick = {
                            if (mode == RecordingAreaMode.CUSTOM) {
                                showCustomAreaDialog = true
                            } else {
                                val nextRect = when (mode) {
                                    RecordingAreaMode.FULL_SCREEN -> NormalizedRect.FULL
                                    else -> NormalizedRect.forAspectRatio(
                                        targetRatio = mode.aspectRatio ?: (9f / 16f),
                                        containerRatio = 9f / 16f
                                    )
                                }
                                onSelectArea(mode, nextRect)
                            }
                        },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("area_chip_${mode.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(visible = settings.areaMode != RecordingAreaMode.FULL_SCREEN) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.area_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InteractiveAreaFramePreview(
                        rect = settings.customArea,
                        resolution = settings.resolution,
                        onRectChanged = { newRect ->
                            if (enabled) onSelectArea(RecordingAreaMode.CUSTOM, newRect)
                        }
                    )
                    OutlinedButton(
                        onClick = { showCustomAreaDialog = true },
                        enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("edit_custom_area_button")
                    ) {
                        Icon(Icons.Rounded.Crop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.custom_area_edit_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioCaptureCard(
    selectedMode: AudioSourceMode,
    hasMicPermission: Boolean,
    enabled: Boolean,
    onSelectMode: (AudioSourceMode) -> Unit
) {
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
                eyebrow = "Acoustic Input",
                title = stringResource(R.string.section_audio_capture),
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (hasMicPermission) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = if (hasMicPermission) "MIC READY" else "NEEDS MIC",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasMicPermission) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioSourceMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    val icon = when (mode) {
                        AudioSourceMode.MICROPHONE -> Icons.Rounded.Mic
                        AudioSourceMode.INTERNAL -> Icons.Rounded.VolumeUp
                        AudioSourceMode.MIC_AND_INTERNAL -> Icons.Rounded.GraphicEq
                        AudioSourceMode.MUTE -> Icons.Outlined.MicOff
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = enabled) { onSelectMode(mode) }
                            .testTag("audio_mode_${mode.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = mode.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Text(
                text = selectedMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HdOutputProfileCard(
    settings: CaptureSettings,
    enabled: Boolean,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFormat: (VideoExportFormat) -> Unit,
    onSelectFps: (Int) -> Unit
) {
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
                eyebrow = "Multi-Format HD",
                title = stringResource(R.string.section_output_profile)
            )

            // Resolution Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoResolution.entries.forEach { res ->
                    val selected = res == settings.resolution
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        onClick = { onSelectResolution(res) },
                        label = {
                            Text(
                                text = res.shortLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("capture_res_${res.shortLabel.lowercase()}")
                    )
                }
            }

            // Format & FPS Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoExportFormat.entries.forEach { fmt ->
                    val selected = fmt == settings.exportFormat
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        onClick = { onSelectFormat(fmt) },
                        label = { Text(fmt.shortLabel, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("capture_fmt_${fmt.name.lowercase()}")
                    )
                }

                listOf(30, 60).forEach { fps ->
                    FilterChip(
                        selected = settings.frameRate == fps,
                        enabled = enabled,
                        onClick = { onSelectFps(fps) },
                        label = { Text("${fps} FPS", style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("capture_fps_$fps")
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingShutterSetupCard(
    enabled: Boolean,
    hasOverlayPermission: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.section_floating_shutter),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1080p PNG · Hidden from recording",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                StudioSwitch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("floating_shutter_switch")
                )
            }

            Text(
                text = stringResource(R.string.floating_shutter_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (enabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (hasOverlayPermission) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasOverlayPermission) {
                                stringResource(R.string.permission_overlay_active)
                            } else {
                                "Overlay permission required for floating button"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!hasOverlayPermission) {
                        OutlinedButton(
                            onClick = onRequestOverlayPermission,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_overlay_button")
                        ) {
                            Text(
                                text = stringResource(R.string.permission_overlay_needed),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
