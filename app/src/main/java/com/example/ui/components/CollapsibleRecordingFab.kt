package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.TallyAmber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val FAB_AUTO_COLLAPSE_TIMEOUT_MS = 2_000L

/**
 * Deterministic state controller for a collapsible floating action button that expands on
 * interaction and automatically collapses back to its single-icon state after [timeoutMs].
 */
class CollapsibleFabStateController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = FAB_AUTO_COLLAPSE_TIMEOUT_MS
) {
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private var collapseJob: Job? = null

    fun expand() {
        _isExpanded.value = true
        restartAutoCollapseTimer()
    }

    fun onInteractionWhileExpanded() {
        if (_isExpanded.value) {
            restartAutoCollapseTimer()
        }
    }

    fun collapseNow() {
        collapseJob?.cancel()
        collapseJob = null
        _isExpanded.value = false
    }

    private fun restartAutoCollapseTimer() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(timeoutMs)
            _isExpanded.value = false
        }
    }
}

/**
 * In-app collapsible Floating Action Button for active screen recordings (shown when the system-wide
 * overlay is unavailable/disabled). Starts as a single round icon, expands into Stop, Pause/Resume,
 * and 1080p Screenshot controls on tap, and automatically collapses back after 2 seconds.
 */
@Composable
fun CollapsibleRecordingFab(
    isPaused: Boolean,
    opacity: Float,
    onStopClick: () -> Unit,
    onTogglePauseClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val controller = remember(scope) { CollapsibleFabStateController(scope = scope) }
    val isExpanded by controller.isExpanded.collectAsState()

    AnimatedContent(
        targetState = isExpanded,
        transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(
                initialScale = 0.84f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.84f))
        },
        label = "collapsible_recording_fab",
        modifier = modifier.alpha(opacity.coerceIn(0.35f, 1f))
    ) { expanded ->
        if (!expanded) {
            val ringColor = if (isPaused) TallyAmber else StudioGreen
            FloatingActionButton(
                onClick = { controller.expand() },
                shape = CircleShape,
                containerColor = Color(0xEC141820),
                contentColor = ringColor,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("floating_fab_collapsed")
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.5.dp, ringColor),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.Pause else Icons.Rounded.FiberManualRecord,
                            contentDescription = "Expand recording controls",
                            tint = ringColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xEC10151E),
                border = BorderStroke(1.5.dp, StudioGreen.copy(alpha = 0.5f)),
                shadowElevation = 10.dp,
                modifier = Modifier.testTag("floating_fab_expanded_panel")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stop
                    FabActionCircle(
                        ringColor = Color(0xFFFF3B4E),
                        fillColor = Color(0xFF141820),
                        tag = "floating_fab_stop",
                        onClick = {
                            controller.collapseNow()
                            onStopClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop screen recording",
                            tint = Color(0xFFFF3B4E),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Pause / Resume
                    FabActionCircle(
                        ringColor = TallyAmber,
                        fillColor = if (isPaused) TallyAmber else Color(0xFF141820),
                        tag = "floating_fab_pause",
                        onClick = {
                            controller.onInteractionWhileExpanded()
                            onTogglePauseClick()
                        }
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isPaused) "Resume screen recording" else "Pause screen recording",
                            tint = if (isPaused) Color(0xFF141820) else TallyAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 1080p Screenshot Capture
                    FabActionCircle(
                        ringColor = StudioGreen,
                        fillColor = Color(0xFF141820),
                        tag = "floating_fab_screenshot",
                        onClick = {
                            controller.collapseNow()
                            onScreenshotClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Capture 1080p screenshot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FabActionCircle(
    ringColor: Color,
    fillColor: Color,
    tag: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(2.dp, ringColor),
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .testTag(tag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}
