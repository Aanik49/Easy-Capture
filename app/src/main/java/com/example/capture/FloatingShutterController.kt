package com.example.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Compact draggable floating screen-recording overlay with:
 * - 1080p Stealth Screenshot Shutter
 * - Pause / Resume Recording toggle (temporarily pauses without finishing the file)
 * - Stop Recording button
 *
 * Snaps smoothly to screen edges with configurable opacity and hides itself synchronously
 * before GPU frame capture so neither the screenshot nor the recording shows capture clutter.
 */
class FloatingShutterController(
    private val context: Context,
    private val initialOpacity: Float,
    private val onScreenshotClicked: () -> Unit,
    private val onPauseResumeClicked: () -> Unit,
    private val onStopClicked: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: LinearLayout? = null
    private var pauseResumeView: OverlayGlyphView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var isPausedState = false

    fun show() {
        mainHandler.post {
            if (isAttached) return@post
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return@post
            }

            val dp = { value: Float ->
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    context.resources.displayMetrics
                ).toInt()
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                alpha = initialOpacity.coerceIn(0.35f, 1f)
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            }

            val shutterButton = OverlayGlyphView(context, OverlayButtonType.SCREENSHOT).apply {
                contentDescription = "Capture 1080p screenshot"
                layoutParams = LinearLayout.LayoutParams(dp(52f), dp(52f))
            }

            val pauseResumeButton = OverlayGlyphView(context, OverlayButtonType.PAUSE_RESUME).apply {
                isPaused = isPausedState
                contentDescription = if (isPausedState) "Resume screen recording" else "Pause screen recording"
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                    topMargin = dp(8f)
                }
            }

            val stopButton = OverlayGlyphView(context, OverlayButtonType.STOP).apply {
                contentDescription = "Stop screen recording"
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                    topMargin = dp(8f)
                }
            }

            container.addView(shutterButton)
            container.addView(pauseResumeButton)
            container.addView(stopButton)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = context.resources.displayMetrics
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = displayMetrics.widthPixels - dp(68f)
                y = (displayMetrics.heightPixels * 0.34f).toInt()
            }

            attachDragAndClick(shutterButton, params, onClick = onScreenshotClicked)
            attachDragAndClick(pauseResumeButton, params, onClick = onPauseResumeClicked)
            attachDragAndClick(stopButton, params, onClick = onStopClicked)

            try {
                windowManager.addView(container, params)
                rootView = container
                pauseResumeView = pauseResumeButton
                layoutParams = params
                isAttached = true
            } catch (_: Exception) {
                rootView = null
                pauseResumeView = null
                layoutParams = null
                isAttached = false
            }
        }
    }

    fun setPausedState(paused: Boolean) {
        val action: () -> Unit = {
            isPausedState = paused
            pauseResumeView?.let { btn ->
                btn.isPaused = paused
                btn.contentDescription = if (paused) "Resume screen recording" else "Pause screen recording"
                btn.invalidate()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun attachDragAndClick(
        targetView: View,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        val touchSlopPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics
        )

        targetView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                        dragging = true
                    }
                    if (dragging && isAttached) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching {
                            rootView?.let { windowManager.updateViewLayout(it, params) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                        onClick()
                    } else if (dragging && isAttached) {
                        // Snap gently to nearest horizontal screen edge so it never blocks center content
                        val screenW = context.resources.displayMetrics.widthPixels
                        val viewW = rootView?.width ?: 140
                        val margin = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics
                        ).toInt()
                        params.x = if (params.x + viewW / 2 < screenW / 2) margin else screenW - viewW - margin
                        runCatching {
                            rootView?.let { windowManager.updateViewLayout(it, params) }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Hides or restores the floating overlay instantly during silent 1080p screenshot capture.
     */
    fun setHiddenForScreenshot(hidden: Boolean) {
        val action = {
            rootView?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (!isAttached) return@post
            try {
                rootView?.let { windowManager.removeViewImmediate(it) }
            } catch (_: Exception) {
            }
            rootView = null
            pauseResumeView = null
            layoutParams = null
            isAttached = false
        }
    }

    private enum class OverlayButtonType {
        SCREENSHOT,
        PAUSE_RESUME,
        STOP
    }

    private class OverlayGlyphView(
        context: Context,
        private val type: OverlayButtonType
    ) : View(context) {

        var isPaused: Boolean = false

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6141820")
            style = Paint.Style.FILL
        }
        private val pausedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0FFB020")
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokeGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00D4B4")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val badgeRect = RectF()
        private val playPath = Path()

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = (minOf(w, h) / 2f) - (w * 0.06f)

            val showPausedFill = type == OverlayButtonType.PAUSE_RESUME && isPaused
            canvas.drawCircle(cx, cy, radius, if (showPausedFill) pausedBgPaint else bgPaint)

            ringPaint.color = when (type) {
                OverlayButtonType.SCREENSHOT -> Color.parseColor("#00D4B4")
                OverlayButtonType.PAUSE_RESUME -> Color.parseColor("#FFB020")
                OverlayButtonType.STOP -> Color.parseColor("#FF3B4E")
            }
            ringPaint.strokeWidth = w * 0.065f
            canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)

            when (type) {
                OverlayButtonType.STOP -> {
                    glyphPaint.color = Color.parseColor("#FF3B4E")
                    val boxHalf = w * 0.17f
                    badgeRect.set(cx - boxHalf, cy - boxHalf, cx + boxHalf, cy + boxHalf)
                    canvas.drawRoundRect(badgeRect, w * 0.07f, w * 0.07f, glyphPaint)
                }

                OverlayButtonType.PAUSE_RESUME -> {
                    if (isPaused) {
                        // Crisp dark play triangle on bright amber pill to indicate Resume
                        glyphPaint.color = Color.parseColor("#141820")
                        val triH = h * 0.34f
                        val triW = w * 0.28f
                        val leftX = cx - triW * 0.38f
                        playPath.reset()
                        playPath.moveTo(leftX, cy - triH / 2f)
                        playPath.lineTo(leftX + triW, cy)
                        playPath.lineTo(leftX, cy + triH / 2f)
                        playPath.close()
                        canvas.drawPath(playPath, glyphPaint)
                    } else {
                        // Crisp dual pause bars in amber while recording
                        glyphPaint.color = Color.parseColor("#FFB020")
                        val barW = w * 0.105f
                        val barH = h * 0.34f
                        val gap = w * 0.095f
                        val corner = w * 0.04f
                        badgeRect.set(
                            cx - gap / 2f - barW,
                            cy - barH / 2f,
                            cx - gap / 2f,
                            cy + barH / 2f
                        )
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                        badgeRect.set(
                            cx + gap / 2f,
                            cy - barH / 2f,
                            cx + gap / 2f + barW,
                            cy + barH / 2f
                        )
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                    }
                }

                OverlayButtonType.SCREENSHOT -> {
                    // Crisp camera viewfinder + shutter dot glyph
                    glyphPaint.color = Color.WHITE
                    val bodyW = w * 0.44f
                    val bodyH = h * 0.30f
                    badgeRect.set(
                        cx - bodyW / 2f,
                        cy - bodyH / 2f + h * 0.03f,
                        cx + bodyW / 2f,
                        cy + bodyH / 2f + h * 0.03f
                    )
                    strokeGlyphPaint.strokeWidth = w * 0.055f
                    canvas.drawRoundRect(badgeRect, w * 0.07f, w * 0.07f, strokeGlyphPaint)
                    canvas.drawLine(
                        cx - w * 0.08f, badgeRect.top - h * 0.055f,
                        cx + w * 0.08f, badgeRect.top - h * 0.055f,
                        strokeGlyphPaint
                    )
                    canvas.drawCircle(cx, badgeRect.centerY(), w * 0.085f, glyphPaint)
                }
            }
        }
    }
}
