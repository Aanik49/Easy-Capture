package com.example.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Collapsible draggable floating screen-recording button:
 * - Appears initially as a single compact round button docked to the screen edge.
 * - Tapping the round button smoothly expands Stop, Pause/Resume, and 1080p Capture (Screenshot) icons.
 * - Automatically collapses back to the single round button after 2 seconds of inactivity.
 * - Hides itself synchronously before GPU frame capture so neither the screenshot nor recording shows clutter.
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

    private var rootView: FrameLayout? = null
    private var collapsedButtonView: OverlayGlyphView? = null
    private var expandedPanelView: LinearLayout? = null
    private var pauseResumeView: OverlayGlyphView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var isPausedState = false
    private var isExpanded = false

    private val autoCollapseRunnable = Runnable {
        collapseToSingleButton()
    }

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

            val container = FrameLayout(context).apply {
                alpha = initialOpacity.coerceIn(0.35f, 1f)
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            }

            // 1. Collapsed State: Single crisp round floating button
            val singleButton = OverlayGlyphView(context, OverlayButtonType.SINGLE_ORB).apply {
                isPaused = isPausedState
                contentDescription = "Expand recording controls"
                layoutParams = FrameLayout.LayoutParams(dp(54f), dp(54f))
            }

            // 2. Expanded State: Vertical glass pill containing Stop, Pause/Resume, and Capture icons
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30f).toFloat()
                setColor(Color.parseColor("#E610151E"))
                setStroke(dp(1.5f), Color.parseColor("#4D16B364"))
            }

            val expandedPanel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = pillBg
                setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val stopButton = OverlayGlyphView(context, OverlayButtonType.STOP).apply {
                contentDescription = "Stop screen recording"
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f))
            }

            val pauseResumeButton = OverlayGlyphView(context, OverlayButtonType.PAUSE_RESUME).apply {
                isPaused = isPausedState
                contentDescription = if (isPausedState) "Resume screen recording" else "Pause screen recording"
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                    topMargin = dp(8f)
                }
            }

            val captureButton = OverlayGlyphView(context, OverlayButtonType.SCREENSHOT).apply {
                contentDescription = "Capture 1080p screenshot"
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                    topMargin = dp(8f)
                }
            }

            expandedPanel.addView(stopButton)
            expandedPanel.addView(pauseResumeButton)
            expandedPanel.addView(captureButton)

            container.addView(singleButton)
            container.addView(expandedPanel)

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
                x = displayMetrics.widthPixels - dp(66f)
                y = (displayMetrics.heightPixels * 0.36f).toInt()
            }

            attachDragAndClick(singleButton, params, onClick = {
                expandControls()
            })
            attachDragAndClick(stopButton, params, onClick = {
                mainHandler.removeCallbacks(autoCollapseRunnable)
                onStopClicked()
            })
            attachDragAndClick(pauseResumeButton, params, onClick = {
                scheduleAutoCollapse()
                onPauseResumeClicked()
            })
            attachDragAndClick(captureButton, params, onClick = {
                collapseToSingleButton()
                onScreenshotClicked()
            })

            try {
                windowManager.addView(container, params)
                rootView = container
                collapsedButtonView = singleButton
                expandedPanelView = expandedPanel
                pauseResumeView = pauseResumeButton
                layoutParams = params
                isAttached = true
                isExpanded = false
            } catch (_: Exception) {
                rootView = null
                collapsedButtonView = null
                expandedPanelView = null
                pauseResumeView = null
                layoutParams = null
                isAttached = false
            }
        }
    }

    private fun expandControls() {
        if (!isAttached || isExpanded) return
        val single = collapsedButtonView ?: return
        val panel = expandedPanelView ?: return
        val params = layoutParams ?: return

        isExpanded = true
        single.visibility = View.GONE
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.86f
        panel.scaleY = 0.86f
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(140L)
            .start()

        // Clamp Y so the expanded 3-button pill never clips past the bottom edge
        val dm = context.resources.displayMetrics
        val expandedEstHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 186f, dm).toInt()
        val topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, dm).toInt()
        val maxY = (dm.heightPixels - expandedEstHeight - topMargin).coerceAtLeast(topMargin)
        if (params.y > maxY) {
            params.y = maxY
            runCatching { rootView?.let { windowManager.updateViewLayout(it, params) } }
        }

        scheduleAutoCollapse()
    }

    private fun collapseToSingleButton() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        if (!isAttached || !isExpanded) return
        val single = collapsedButtonView ?: return
        val panel = expandedPanelView ?: return

        isExpanded = false
        panel.animate().cancel()
        panel.visibility = View.GONE
        single.visibility = View.VISIBLE
        single.alpha = 0.85f
        single.scaleX = 0.88f
        single.scaleY = 0.88f
        single.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(130L)
            .start()

        mainHandler.post { snapToNearestEdge() }
    }

    private fun scheduleAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        mainHandler.postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_DELAY_MS)
    }

    private fun snapToNearestEdge() {
        val params = layoutParams ?: return
        val root = rootView ?: return
        if (!isAttached) return
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dm).toInt()
        val viewW = if (root.width > 0) root.width else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 58f, dm).toInt()
        params.x = if (params.x + viewW / 2 < screenW / 2) margin else (screenW - viewW - margin).coerceAtLeast(margin)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    fun setPausedState(paused: Boolean) {
        val action: () -> Unit = {
            isPausedState = paused
            pauseResumeView?.let { btn ->
                btn.isPaused = paused
                btn.contentDescription = if (paused) "Resume screen recording" else "Pause screen recording"
                btn.invalidate()
            }
            collapsedButtonView?.let { orb ->
                orb.isPaused = paused
                orb.invalidate()
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
                    if (isExpanded) {
                        // Pause auto-collapse while user is actively interacting
                        mainHandler.removeCallbacks(autoCollapseRunnable)
                    }
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
                        snapToNearestEdge()
                        if (isExpanded) {
                            scheduleAutoCollapse()
                        }
                    } else if (isExpanded) {
                        scheduleAutoCollapse()
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
            mainHandler.removeCallbacks(autoCollapseRunnable)
            if (!isAttached) return@post
            try {
                rootView?.let { windowManager.removeViewImmediate(it) }
            } catch (_: Exception) {
            }
            rootView = null
            collapsedButtonView = null
            expandedPanelView = null
            pauseResumeView = null
            layoutParams = null
            isAttached = false
            isExpanded = false
        }
    }

    private enum class OverlayButtonType {
        SINGLE_ORB,
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
            color = Color.parseColor("#EC141820")
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
            color = Color.parseColor("#16B364")
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
            val radius = (minOf(w, h) / 2f) - (w * 0.05f)

            val showPausedFill = type == OverlayButtonType.PAUSE_RESUME && isPaused
            canvas.drawCircle(cx, cy, radius, if (showPausedFill) pausedBgPaint else bgPaint)

            ringPaint.color = when (type) {
                OverlayButtonType.SINGLE_ORB -> if (isPaused) Color.parseColor("#FFB020") else Color.parseColor("#16B364")
                OverlayButtonType.SCREENSHOT -> Color.parseColor("#16B364")
                OverlayButtonType.PAUSE_RESUME -> Color.parseColor("#FFB020")
                OverlayButtonType.STOP -> Color.parseColor("#FF3B4E")
            }
            ringPaint.strokeWidth = w * 0.068f
            canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)

            when (type) {
                OverlayButtonType.SINGLE_ORB -> {
                    if (isPaused) {
                        // Subtle amber pause bars inside collapsed single orb when paused
                        glyphPaint.color = Color.parseColor("#FFB020")
                        val barW = w * 0.095f
                        val barH = h * 0.30f
                        val gap = w * 0.085f
                        val corner = w * 0.035f
                        badgeRect.set(cx - gap / 2f - barW, cy - barH / 2f, cx - gap / 2f, cy + barH / 2f)
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                        badgeRect.set(cx + gap / 2f, cy - barH / 2f, cx + gap / 2f + barW, cy + barH / 2f)
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                    } else {
                        // Clean emerald studio record core dot + subtle outer halo
                        glyphPaint.color = Color.parseColor("#4016B364")
                        canvas.drawCircle(cx, cy, w * 0.25f, glyphPaint)
                        glyphPaint.color = Color.parseColor("#16B364")
                        canvas.drawCircle(cx, cy, w * 0.155f, glyphPaint)
                        glyphPaint.color = Color.WHITE
                        canvas.drawCircle(cx - w * 0.045f, cy - h * 0.045f, w * 0.035f, glyphPaint)
                    }
                }

                OverlayButtonType.STOP -> {
                    glyphPaint.color = Color.parseColor("#FF3B4E")
                    val boxHalf = w * 0.17f
                    badgeRect.set(cx - boxHalf, cy - boxHalf, cx + boxHalf, cy + boxHalf)
                    canvas.drawRoundRect(badgeRect, w * 0.07f, w * 0.07f, glyphPaint)
                }

                OverlayButtonType.PAUSE_RESUME -> {
                    if (isPaused) {
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
                        glyphPaint.color = Color.parseColor("#FFB020")
                        val barW = w * 0.105f
                        val barH = h * 0.34f
                        val gap = w * 0.095f
                        val corner = w * 0.04f
                        badgeRect.set(cx - gap / 2f - barW, cy - barH / 2f, cx - gap / 2f, cy + barH / 2f)
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                        badgeRect.set(cx + gap / 2f, cy - barH / 2f, cx + gap / 2f + barW, cy + barH / 2f)
                        canvas.drawRoundRect(badgeRect, corner, corner, glyphPaint)
                    }
                }

                OverlayButtonType.SCREENSHOT -> {
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

    companion object {
        private const val AUTO_COLLAPSE_DELAY_MS = 2_000L
    }
}
