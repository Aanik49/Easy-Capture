package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CaptureSettings
import com.example.data.MediaStorageHelper
import com.example.data.ThemeMode
import com.example.data.VideoExportFormat
import com.example.data.VideoResolution
import com.example.ui.components.StudioSectionHeader
import com.example.ui.components.StudioSwitch
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: CaptureSettings,
    hasMicPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFormat: (VideoExportFormat) -> Unit,
    onSelectFps: (Int) -> Unit,
    onSelectBitrateMbps: (Int) -> Unit,
    onSelectCountdown: (Int) -> Unit,
    onToggleFloatingEnabled: (Boolean) -> Unit,
    onUpdateFloatingOpacity: (Float) -> Unit,
    onToggleHapticEnabled: (Boolean) -> Unit,
    onToggleShowScreenTouches: (Boolean) -> Unit,
    onToggleQuickControlBar: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleVideoName = remember(settings.exportFormat) {
        MediaStorageHelper.sampleFileNamePreview(settings.exportFormat.fileExtension)
    }
    val samplePhotoName = remember {
        MediaStorageHelper.sampleFileNamePreview("png")
    }

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
            // Appearance & Dark Mode
            item(key = "appearance") {
                SettingsGroupCard(
                    eyebrow = "Interface Theme",
                    title = stringResource(R.string.settings_appearance)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            val icon = when (mode) {
                                ThemeMode.DARK -> Icons.Rounded.DarkMode
                                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                ThemeMode.SYSTEM -> Icons.Rounded.Brightness4
                            }
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Recording Defaults
            item(key = "recording_defaults") {
                SettingsGroupCard(
                    eyebrow = "Capture Engine",
                    title = stringResource(R.string.settings_recording)
                ) {
                    SettingChipRow(
                        label = "Default Resolution",
                        items = VideoResolution.entries.map { it.shortLabel to (it == settings.resolution) },
                        onClick = { idx -> onSelectResolution(VideoResolution.entries[idx]) },
                        tagPrefix = "settings_res"
                    )

                    SettingChipRow(
                        label = "Export Container & Codec",
                        items = VideoExportFormat.entries.map { it.shortLabel to (it == settings.exportFormat) },
                        onClick = { idx -> onSelectFormat(VideoExportFormat.entries[idx]) },
                        tagPrefix = "settings_fmt"
                    )

                    val bitrates = listOf(8, 16, 24)
                    SettingChipRow(
                        label = "Video Bitrate",
                        items = bitrates.map { "${it} Mbps" to (settings.bitrateMbps == it) },
                        onClick = { idx -> onSelectBitrateMbps(bitrates[idx]) },
                        tagPrefix = "settings_bitrate"
                    )

                    val fpsList = listOf(30, 60)
                    SettingChipRow(
                        label = "Frame Rate",
                        items = fpsList.map { "${it} FPS" to (settings.frameRate == it) },
                        onClick = { idx -> onSelectFps(fpsList[idx]) },
                        tagPrefix = "settings_fps"
                    )

                    val countdowns = listOf(0, 3, 5)
                    SettingChipRow(
                        label = "Countdown Before Start",
                        items = countdowns.map { (if (it == 0) "Off" else "${it}s") to (settings.countdownSeconds == it) },
                        onClick = { idx -> onSelectCountdown(countdowns[idx]) },
                        tagPrefix = "settings_countdown"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_show_touches),
                        subtitle = stringResource(R.string.settings_show_touches_desc),
                        checked = settings.showScreenTouches,
                        onCheckedChange = onToggleShowScreenTouches,
                        tag = "settings_show_touches_toggle"
                    )

                    if (settings.showScreenTouches) {
                        OutlinedButton(
                            onClick = onOpenDeveloperOptions,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_dev_show_taps_button")
                        ) {
                            Text(
                                text = "System-Wide Touch Dots (Developer Options)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_quick_bar),
                        subtitle = stringResource(R.string.settings_quick_bar_desc),
                        checked = settings.quickControlBarEnabled,
                        onCheckedChange = onToggleQuickControlBar,
                        tag = "settings_quick_bar_toggle"
                    )
                }
            }

            // Stealth Floating 1080p Screenshot Button
            item(key = "floating_shutter_settings") {
                SettingsGroupCard(
                    eyebrow = "Stealth 1080p Snap",
                    title = stringResource(R.string.settings_floating)
                ) {
                    SettingsSwitchRow(
                        title = "Show Floating Shutter Button",
                        subtitle = "Auto-hides cleanly during screenshot capture so it never appears in video",
                        checked = settings.floatingButtonEnabled,
                        onCheckedChange = onToggleFloatingEnabled,
                        tag = "settings_floating_toggle"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Floating Button Opacity",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(settings.floatingButtonOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = settings.floatingButtonOpacity,
                            onValueChange = onUpdateFloatingOpacity,
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("settings_floating_opacity")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = "Haptic Save Confirmation",
                        subtitle = "Subtle vibration when 1080p screenshot or recording auto-saves",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = onToggleHapticEnabled,
                        tag = "settings_haptic_toggle"
                    )
                }
            }

            // Permissions Checklist
            item(key = "permissions") {
                SettingsGroupCard(
                    eyebrow = "System Access",
                    title = stringResource(R.string.settings_permissions)
                ) {
                    PermissionRow(
                        icon = Icons.Rounded.Mic,
                        title = "Microphone & Real-Time Audio",
                        granted = hasMicPermission,
                        onGrant = onRequestMicPermission,
                        tag = "perm_mic_button"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        title = "Floating 1080p Shutter Overlay",
                        granted = hasOverlayPermission,
                        onGrant = onRequestOverlayPermission,
                        tag = "perm_overlay_button"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Recording Control Notification",
                        granted = hasNotificationPermission,
                        onGrant = onRequestNotificationPermission,
                        tag = "perm_notif_button"
                    )
                }
            }

            // Storage Directory & Naming Convention Info
            item(key = "storage_info") {
                SettingsGroupCard(
                    eyebrow = "Direct Device Storage",
                    title = stringResource(R.string.settings_storage)
                ) {
                    StoragePathRow(
                        label = "Recordings & HD Exports",
                        directory = "Movies/EasyCapture/",
                        exampleFile = sampleVideoName
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StoragePathRow(
                        label = "1080p Screenshots",
                        directory = "Pictures/EasyCapture/",
                        exampleFile = samplePhotoName
                    )
                    Text(
                        text = "Captures are saved automatically to your device storage directories and are not duplicated inside the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    eyebrow: String,
    title: String,
    content: @Composable () -> Unit
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
            StudioSectionHeader(eyebrow = eyebrow, title = title)
            content()
        }
    }
}

@Composable
private fun SettingChipRow(
    label: String,
    items: List<Pair<String, Boolean>>,
    onClick: (Int) -> Unit,
    tagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { idx, (text, selected) ->
                FilterChip(
                    selected = selected,
                    onClick = { onClick(idx) },
                    label = { Text(text, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("${tagPrefix}_$idx")
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StudioSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onGrant: () -> Unit,
    tag: String
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            OutlinedButton(
                onClick = onGrant,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag(tag)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StoragePathRow(
    label: String,
    directory: String,
    exampleFile: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$directory$exampleFile",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
