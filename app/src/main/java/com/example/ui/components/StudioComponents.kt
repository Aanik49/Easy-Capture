package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.NormalizedRect
import com.example.data.VideoResolution
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Consistent, high-contrast Material 3 toggle switch across Dark & Light themes.
 * Explicitly styles unchecked (off) and disabled tracks, thumbs, and borders so the knob
 * and pill outline stay crisp and legible in every state.
 */
@Composable
fun StudioSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.65f),
            disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledCheckedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier
    )
}

@Composable
fun StudioSectionHeader(
    eyebrow: String,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun BroadcastAudioMeter(
    level: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    segments: Int = 18
) {
    val activeColorLow = MaterialTheme.colorScheme.secondary
    val activeColorMid = MaterialTheme.colorScheme.tertiary
    val activeColorPeak = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val gap = 3.dp.toPx()
        val segWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
        val litCount = if (isActive) (level.coerceIn(0f, 1f) * segments).roundToInt() else 0

        for (i in 0 until segments) {
            val fraction = (i + 1).toFloat() / segments
            val color = when {
                i >= litCount -> inactiveColor
                fraction > 0.8f -> activeColorPeak
                fraction > 0.55f -> activeColorMid
                else -> activeColorLow
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (segWidth + gap), 0f),
                size = Size(segWidth, size.height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

@Composable
fun InteractiveAreaFramePreview(
    rect: NormalizedRect,
    resolution: VideoResolution,
    onRectChanged: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier,
    previewWidthDp: Dp = 172.dp
) {
    val frameGreen = MaterialTheme.colorScheme.primary
    val accentCyan = MaterialTheme.colorScheme.secondary
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderCol = MaterialTheme.colorScheme.outline

    var currentRect by remember(rect) { mutableStateOf(rect.sanitized()) }
    var activeHandle by remember { mutableStateOf<DragCorner?>(null) }

    val estW = ((resolution.shortEdge * currentRect.width).roundToInt() / 16).coerceAtLeast(16) * 16
    val estH = ((resolution.longEdge * currentRect.height).roundToInt() / 16).coerceAtLeast(16) * 16

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(previewWidthDp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(surfaceBg)
                .border(1.5.dp, borderCol, RoundedCornerShape(18.dp))
                .testTag("recording_area_canvas")
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val nx = offset.x / w
                            val ny = offset.y / h
                            val r = currentRect
                            val corners = listOf(
                                DragCorner.TOP_LEFT to hypot(nx - r.left, ny - r.top),
                                DragCorner.TOP_RIGHT to hypot(nx - r.right, ny - r.top),
                                DragCorner.BOTTOM_LEFT to hypot(nx - r.left, ny - r.bottom),
                                DragCorner.BOTTOM_RIGHT to hypot(nx - r.right, ny - r.bottom)
                            )
                            val nearest = corners.minByOrNull { it.second }
                            activeHandle = if (nearest != null && nearest.second < 0.22f) {
                                nearest.first
                            } else {
                                DragCorner.CENTER
                            }
                        },
                        onDragEnd = {
                            activeHandle = null
                            onRectChanged(currentRect.sanitized())
                        },
                        onDragCancel = {
                            activeHandle = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h
                            val r = currentRect
                            val minSpan = 0.22f

                            currentRect = when (activeHandle) {
                                DragCorner.TOP_LEFT -> r.copy(
                                    left = (r.left + dx).coerceIn(0f, r.right - minSpan),
                                    top = (r.top + dy).coerceIn(0f, r.bottom - minSpan)
                                )
                                DragCorner.TOP_RIGHT -> r.copy(
                                    right = (r.right + dx).coerceIn(r.left + minSpan, 1f),
                                    top = (r.top + dy).coerceIn(0f, r.bottom - minSpan)
                                )
                                DragCorner.BOTTOM_LEFT -> r.copy(
                                    left = (r.left + dx).coerceIn(0f, r.right - minSpan),
                                    bottom = (r.bottom + dy).coerceIn(r.top + minSpan, 1f)
                                )
                                DragCorner.BOTTOM_RIGHT -> r.copy(
                                    right = (r.right + dx).coerceIn(r.left + minSpan, 1f),
                                    bottom = (r.bottom + dy).coerceIn(r.top + minSpan, 1f)
                                )
                                else -> {
                                    val spanW = r.right - r.left
                                    val spanH = r.bottom - r.top
                                    val newL = (r.left + dx).coerceIn(0f, 1f - spanW)
                                    val newT = (r.top + dy).coerceIn(0f, 1f - spanH)
                                    NormalizedRect(newL, newT, newL + spanW, newT + spanH)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Subtle mock screen layout lines to convey orientation & framing
                val mockCol = borderCol.copy(alpha = 0.25f)
                drawRoundRect(
                    color = mockCol,
                    topLeft = Offset(w * 0.10f, h * 0.08f),
                    size = Size(w * 0.80f, h * 0.14f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    color = mockCol,
                    topLeft = Offset(w * 0.10f, h * 0.26f),
                    size = Size(w * 0.80f, h * 0.34f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    color = mockCol,
                    topLeft = Offset(w * 0.10f, h * 0.65f),
                    size = Size(w * 0.37f, h * 0.20f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    color = mockCol,
                    topLeft = Offset(w * 0.53f, h * 0.65f),
                    size = Size(w * 0.37f, h * 0.20f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                val l = currentRect.left * w
                val t = currentRect.top * h
                val r = currentRect.right * w
                val b = currentRect.bottom * h

                // Dim outside selected capture region
                val dim = Color.Black.copy(alpha = 0.56f)
                drawRect(dim, Offset(0f, 0f), Size(w, t))
                drawRect(dim, Offset(0f, b), Size(w, h - b))
                drawRect(dim, Offset(0f, t), Size(l, b - t))
                drawRect(dim, Offset(r, t), Size(w - r, b - t))

                // Rule-of-thirds dashed viewfinder grid inside active frame
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                val gridColor = accentCyan.copy(alpha = 0.35f)
                val thirdW = (r - l) / 3f
                val thirdH = (b - t) / 3f
                for (i in 1..2) {
                    drawLine(gridColor, Offset(l + thirdW * i, t), Offset(l + thirdW * i, b), 1f, pathEffect = dash)
                    drawLine(gridColor, Offset(l, t + thirdH * i), Offset(r, t + thirdH * i), 1f, pathEffect = dash)
                }

                // Active capture frame border
                drawRect(
                    color = frameGreen,
                    topLeft = Offset(l, t),
                    size = Size(r - l, b - t),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Corner drag handles
                val handleR = 6.dp.toPx()
                val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
                corners.forEach { pt ->
                    drawCircle(color = frameGreen, radius = handleR, center = pt)
                    drawCircle(color = Color.White, radius = handleR * 0.45f, center = pt)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = "OUTPUT  ${estW} × ${estH} px  (${(currentRect.width * 100).roundToInt()}% × ${(currentRect.height * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun AutoSaveStatusBanner(
    fileName: String?,
    directory: String?,
    mediaType: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = fileName != null || errorMessage != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        val isError = errorMessage != null
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            border = BorderStroke(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isError) "Capture Notice" else "${mediaType ?: "File"} auto-saved",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = errorMessage ?: "$directory/$fileName",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = (if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                            .copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_status_banner")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss notification",
                        tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

fun formatDurationTimer(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (safe % 1000L) / 100L
    return String.format("%02d:%02d.%d", minutes, seconds, tenths)
}

fun formatShortTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}
