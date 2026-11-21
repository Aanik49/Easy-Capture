package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CaptureSettings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioObsidian
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple, fast (~900ms) branded studio splash overlay shown once on cold launch.
 */
@Composable
fun EasyCaptureSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }

    val emblemScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "splash_emblem_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "splash_content_alpha"
    )

    LaunchedEffect(Unit) {
        revealed = true
        delay(850L)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle optical radial glow behind emblem
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StudioCyan.copy(alpha = 0.14f),
                        StudioGreen.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.48f
                ),
                radius = size.minDimension * 0.48f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(horizontal = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF0D1117),
                border = BorderStroke(1.5.dp, StudioCyan.copy(alpha = 0.45f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .size(96.dp)
                    .scale(emblemScale)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_easy_capture_fg_clean),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        letterSpacing = 1.6.sp
                    ),
                    color = StudioCyan
                )
            }
        }
    }
}

/**
 * Broadcast-grade pre-recording countdown overlay with animated radial dial,
 * viewfinder corner brackets, active spec badge, and an immediate "Start Now" button.
 */
@Composable
fun BroadcastCountdownOverlay(
    remainingSeconds: Int,
    totalSeconds: Int,
    settings: CaptureSettings,
    onStartNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    translucentBackdrop: Boolean = false
) {
    // Smooth 1-second sweep animation synchronized with every countdown tick
    val secondSweep = remember(remainingSeconds) { Animatable(1f) }
    LaunchedEffect(remainingSeconds) {
        secondSweep.snapTo(1f)
        secondSweep.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 980, easing = LinearEasing)
        )
    }

    val safeTotal = totalSeconds.coerceAtLeast(1)
    val safeRemaining = remainingSeconds.coerceAtLeast(1)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = if (translucentBackdrop) {
                        listOf(
                            Color(0xB80E131D),
                            Color(0xD807090E)
                        )
                    } else {
                        listOf(
                            Color(0xF2121722),
                            Color(0xFA07090E)
                        )
                    }
                )
            )
            .safeDrawingPadding()
            .padding(24.dp)
            .testTag("countdown_overlay")
    ) {
        // Subtle cinema viewfinder corner brackets around the screen frame
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bracketLen = 28.dp.toPx()
            val stroke = 2.dp.toPx()
            val color = StudioCyan.copy(alpha = 0.42f)
            val pad = 4.dp.toPx()
            val l = pad
            val t = pad
            val r = size.width - pad
            val b = size.height - pad

            // Top-left
            drawLine(color, Offset(l, t), Offset(l + bracketLen, t), stroke, StrokeCap.Round)
            drawLine(color, Offset(l, t), Offset(l, t + bracketLen), stroke, StrokeCap.Round)
            // Top-right
            drawLine(color, Offset(r, t), Offset(r - bracketLen, t), stroke, StrokeCap.Round)
            drawLine(color, Offset(r, t), Offset(r, t + bracketLen), stroke, StrokeCap.Round)
            // Bottom-left
            drawLine(color, Offset(l, b), Offset(l + bracketLen, b), stroke, StrokeCap.Round)
            drawLine(color, Offset(l, b), Offset(l, b - bracketLen), stroke, StrokeCap.Round)
            // Bottom-right
            drawLine(color, Offset(r, b), Offset(r - bracketLen, b), stroke, StrokeCap.Round)
            drawLine(color, Offset(r, b), Offset(r, b - bracketLen), stroke, StrokeCap.Round)
        }

        // Top Live Telemetry Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = StudioGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, StudioGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StudioGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.countdown_status_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Text(
                text = "${settings.resolution.shortLabel} · ${settings.frameRate} FPS · ${settings.audioSource.label.uppercase()}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = Color(0xFF9BA7BC)
            )
        }

        // Center Precision Broadcast Dial
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(
                modifier = Modifier.size(228.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerPt = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.minDimension / 2f
                    val tickOuter = outerRadius - 2.dp.toPx()
                    val ringRadius = outerRadius - 20.dp.toPx()

                    // 60 subtle precision broadcast dial ticks
                    val tickCount = 60
                    for (i in 0 until tickCount) {
                        val angleRad = (i * 6.0 - 90.0) * (PI / 180.0)
                        val isMajor = i % 5 == 0
                        val len = if (isMajor) 9.dp.toPx() else 4.5.dp.toPx()
                        val startR = tickOuter - len
                        val startPt = Offset(
                            centerPt.x + (cos(angleRad) * startR).toFloat(),
                            centerPt.y + (sin(angleRad) * startR).toFloat()
                        )
                        val endPt = Offset(
                            centerPt.x + (cos(angleRad) * tickOuter).toFloat(),
                            centerPt.y + (sin(angleRad) * tickOuter).toFloat()
                        )
                        drawLine(
                            color = if (isMajor) StudioCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
                            start = startPt,
                            end = endPt,
                            strokeWidth = if (isMajor) 1.8.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Recessed dark studio dial face
                    drawCircle(
                        color = Color(0xFF121722),
                        radius = ringRadius - 8.dp.toPx(),
                        center = centerPt
                    )
                    drawCircle(
                        color = Color(0xFF263042),
                        radius = ringRadius - 8.dp.toPx(),
                        center = centerPt,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Background track ring
                    val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
                    val arcTopLeft = Offset(centerPt.x - ringRadius, centerPt.y - ringRadius)
                    drawArc(
                        color = Color(0xFF202836),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Smooth sweeping luminous arc
                    val sweepDeg = (360f * secondSweep.value).coerceIn(0f, 360f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.0f to StudioCyan,
                                0.55f to StudioGreen,
                                1.0f to StudioCyan
                            ),
                            center = centerPt
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Animated Crisp Countdown Numeral
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = safeRemaining,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(
                                initialScale = 1.28f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = 0.78f))
                        },
                        label = "countdown_number_anim"
                    ) { count ->
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 76.sp,
                                lineHeight = 80.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.testTag("countdown_number_text")
                        )
                    }

                    Text(
                        text = if (safeRemaining == 1) "SECOND" else "SECONDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            letterSpacing = 1.8.sp
                        ),
                        color = StudioCyan
                    )

                    // Compact step indicator dots
                    if (safeTotal > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (step in safeTotal downTo 1) {
                                val active = step <= safeRemaining
                                Box(
                                    modifier = Modifier
                                        .size(width = if (step == safeRemaining) 14.dp else 6.dp, height = 5.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (step == safeRemaining) StudioGreen
                                            else if (active) StudioCyan.copy(alpha = 0.75f)
                                            else Color.White.copy(alpha = 0.2f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons Below Countdown: Start Immediately + Cancel
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartNow,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_now_countdown_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_start_now),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF334056)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD5DCE8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_countdown_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
