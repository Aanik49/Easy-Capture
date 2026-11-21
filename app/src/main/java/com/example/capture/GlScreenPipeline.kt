package com.example.capture

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.data.NormalizedRect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Hardware-accelerated OpenGL ES 2.0 screen capture pipeline.
 * - Accepts VirtualDisplay output on a single OES SurfaceTexture.
 * - Real-time crops to the selected NormalizedRect recording area and renders directly to MediaCodec encoder surface.
 * - Captures crisp 1080p screenshots directly from an offscreen FBO without stopping/recreating VirtualDisplay
 *   and while holding encoder frame submission during the brief overlay-hide window so no screenshot UI enters video.
 */
class GlScreenPipeline(
    private val displayWidth: Int,
    private val displayHeight: Int,
    private val encodeWidth: Int,
    private val encodeHeight: Int,
    private val cropRect: NormalizedRect,
    private val encoderInputSurface: Surface,
    private val onEncodedFrameAvailable: () -> Unit
) {
    private val glThread = HandlerThread("EasyCapture-GL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var programId = 0
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var virtualDisplaySurface: Surface? = null

    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uSTMatrixLoc = 0

    private val stMatrix = FloatArray(16)
    private val vertexBuffer: FloatBuffer
    private val cropTexCoordBuffer: FloatBuffer
    private val fullTexCoordBuffer: FloatBuffer

    private val released = AtomicBoolean(false)
    @Volatile
    var isPaused: Boolean = false
    @Volatile
    var holdEncoderFrames: Boolean = false

    private var startTimeNs = -1L
    private var totalPausedNs = 0L
    private var pauseStartNs = 0L

    private var pendingScreenshotCallback: ((Bitmap?) -> Unit)? = null
    private var screenshotWaitFrames = 0

    init {
        val vertices = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        vertexBuffer = createFloatBuffer(vertices)

        val clean = cropRect.sanitized()
        val cropUvs = floatArrayOf(
            clean.left,  1f - clean.bottom,
            clean.right, 1f - clean.bottom,
            clean.left,  1f - clean.top,
            clean.right, 1f - clean.top
        )
        cropTexCoordBuffer = createFloatBuffer(cropUvs)

        val fullUvs = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )
        fullTexCoordBuffer = createFloatBuffer(fullUvs)
    }

    fun initializeAndGetInputSurface(): Surface {
        val latch = CountDownLatch(1)
        var initError: Throwable? = null
        glHandler.post {
            try {
                initEgl()
                initGlResources()
            } catch (t: Throwable) {
                initError = t
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        initError?.let { throw it }
        return virtualDisplaySurface
            ?: throw IllegalStateException("Failed to initialize VirtualDisplay input surface")
    }

    fun setPausedState(paused: Boolean) {
        glHandler.post {
            if (paused && !isPaused) {
                isPaused = true
                if (startTimeNs >= 0L) {
                    pauseStartNs = System.nanoTime()
                }
            } else if (!paused && isPaused) {
                if (startTimeNs >= 0L && pauseStartNs > 0L) {
                    totalPausedNs += (System.nanoTime() - pauseStartNs).coerceAtLeast(0L)
                }
                pauseStartNs = 0L
                isPaused = false
            }
        }
    }

    /**
     * Captures a clean 1080p screenshot directly from the GPU OES texture.
     * Waits briefly for a fresh display frame (or falls back to current texture after 90ms if screen is static).
     */
    fun captureClean1080pScreenshot(onComplete: (Bitmap?) -> Unit) {
        if (released.get()) {
            onComplete(null)
            return
        }
        glHandler.post {
            pendingScreenshotCallback = onComplete
            screenshotWaitFrames = 1
            glHandler.postDelayed({
                val cb = pendingScreenshotCallback
                if (cb != null && !released.get()) {
                    pendingScreenshotCallback = null
                    screenshotWaitFrames = 0
                    val bmp = render1080pBitmapFromCurrentTexture()
                    cb(bmp)
                }
            }, 90L)
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        val latch = CountDownLatch(1)
        glHandler.post {
            try {
                pendingScreenshotCallback?.invoke(null)
                pendingScreenshotCallback = null
                virtualDisplaySurface?.release()
                virtualDisplaySurface = null
                surfaceTexture?.setOnFrameAvailableListener(null)
                surfaceTexture?.release()
                surfaceTexture = null

                if (oesTextureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                    oesTextureId = 0
                }
                if (programId != 0) {
                    GLES20.glDeleteProgram(programId)
                    programId = 0
                }
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglTerminate(eglDisplay)
                }
                eglDisplay = EGL14.EGL_NO_DISPLAY
                eglContext = EGL14.EGL_NO_CONTEXT
                eglSurface = EGL14.EGL_NO_SURFACE
            } catch (e: Exception) {
                Log.w(TAG, "GL release error", e)
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        glThread.quitSafely()
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            throw RuntimeException("Unable to find RGB888+recordable EGL config")
        }
        val eglConfig = configs[0]!!

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL window surface")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun initGlResources() {
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uSTMatrixLoc = GLES20.glGetUniformLocation(programId, "uSTMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId).apply {
            setDefaultBufferSize(displayWidth, displayHeight)
            setOnFrameAvailableListener({ onNewFrame() }, glHandler)
        }
        surfaceTexture = st
        virtualDisplaySurface = Surface(st)
    }

    private fun onNewFrame() {
        if (released.get()) return
        val st = surfaceTexture ?: return
        try {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)

            // Handle pending clean 1080p screenshot before forwarding to encoder
            val screenshotCb = pendingScreenshotCallback
            if (screenshotCb != null) {
                if (screenshotWaitFrames > 0) {
                    screenshotWaitFrames--
                }
                if (screenshotWaitFrames <= 0) {
                    pendingScreenshotCallback = null
                    val bmp = render1080pBitmapFromCurrentTexture()
                    screenshotCb(bmp)
                }
            }

            if (isPaused || holdEncoderFrames) {
                return
            }

            val nowNs = System.nanoTime()
            if (startTimeNs < 0L) {
                startTimeNs = nowNs
            }
            val ptsNs = (nowNs - startTimeNs - totalPausedNs).coerceAtLeast(0L)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, encodeWidth, encodeHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            drawQuad(cropTexCoordBuffer)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            onEncodedFrameAvailable()
        } catch (e: Exception) {
            Log.w(TAG, "Frame render error: ${e.message}")
        }
    }

    /**
     * Renders the clean current screen texture into a dedicated 1080p offscreen FBO and reads RGBA pixels.
     * Guarantees exact 1080p resolution (1080x1920 portrait or 1920x1080 landscape, or aspect-locked 1080p short edge).
     */
    private fun render1080pBitmapFromCurrentTexture(): Bitmap? {
        return try {
            val isLandscape = displayWidth >= displayHeight
            val targetW: Int
            val targetH: Int
            if (isLandscape) {
                targetH = 1080
                targetW = ((displayWidth.toFloat() / displayHeight.toFloat()) * 1080f)
                    .roundToInt()
                    .coerceIn(1080, 3840) and 1.inv()
            } else {
                targetW = 1080
                targetH = ((displayHeight.toFloat() / displayWidth.toFloat()) * 1080f)
                    .roundToInt()
                    .coerceIn(1080, 3840) and 1.inv()
            }

            val fboIds = IntArray(1)
            val texIds = IntArray(1)
            GLES20.glGenFramebuffers(1, fboIds, 0)
            GLES20.glGenTextures(1, texIds, 0)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                targetW, targetH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texIds[0],
                0
            )

            GLES20.glViewport(0, 0, targetW, targetH)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawQuad(fullTexCoordBuffer)

            val pixelBuffer = ByteBuffer.allocateDirect(targetW * targetH * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, targetW, targetH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteTextures(1, texIds, 0)
            GLES20.glDeleteFramebuffers(1, fboIds, 0)

            // Flip GL bottom-up pixel rows into standard top-down Android Bitmap
            val rawBytes = ByteArray(targetW * targetH * 4)
            pixelBuffer.rewind()
            pixelBuffer.get(rawBytes)
            val rowSize = targetW * 4
            val flipped = ByteArray(rawBytes.size)
            for (y in 0 until targetH) {
                System.arraycopy(rawBytes, (targetH - 1 - y) * rowSize, flipped, y * rowSize, rowSize)
            }

            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(ByteBuffer.wrap(flipped))
            }
        } catch (e: Exception) {
            Log.e(TAG, "1080p FBO screenshot failed", e)
            null
        }
    }

    private fun drawQuad(texCoords: FloatBuffer) {
        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

        GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoords.position(0)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("GL program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GL shader compile failed: $log")
        }
        return shader
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    companion object {
        private const val TAG = "GlScreenPipeline"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uSTMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uSTMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
