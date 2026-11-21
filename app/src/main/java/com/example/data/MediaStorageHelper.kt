package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedMediaResult(
    val uri: Uri?,
    val fileName: String,
    val displayDirectory: String
)

object MediaStorageHelper {
    const val VIDEO_SUBFOLDER = "EasyCapture"
    const val IMAGE_SUBFOLDER = "EasyCapture"
    const val FILE_PREFIX = "EasyCapture_"

    private val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun buildFileName(extension: String, now: Date = Date()): String {
        val stamp = synchronized(timestampFormatter) {
            timestampFormatter.format(now)
        }
        return "${FILE_PREFIX}${stamp}.${extension.trimStart('.')}"
    }

    fun sampleFileNamePreview(extension: String = "mp4"): String = buildFileName(extension)

    /**
     * Publishes a completed video temp file directly to Movies/EasyCapture/ with EasyCapture_<timestamp>.<ext>
     */
    fun publishVideoToDeviceStorage(
        context: Context,
        tempVideoFile: File,
        format: VideoExportFormat
    ): SavedMediaResult {
        val fileName = buildFileName(format.fileExtension)
        val relativeDir = "${Environment.DIRECTORY_MOVIES}/$VIDEO_SUBFOLDER"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, format.contentMimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create MediaStore video entry")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempVideoFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to open output stream for video")

                val done = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
                SavedMediaResult(uri = uri, fileName = fileName, displayDirectory = relativeDir)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                VIDEO_SUBFOLDER
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val target = File(moviesDir, fileName)
            FileInputStream(tempVideoFile).use { input ->
                FileOutputStream(target).use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf(format.contentMimeType),
                null
            )
            SavedMediaResult(
                uri = Uri.fromFile(target),
                fileName = fileName,
                displayDirectory = relativeDir
            )
        }
    }

    /**
     * Automatically saves a 1080p PNG screenshot directly to Pictures/EasyCapture/EasyCapture_<timestamp>.png
     */
    fun saveScreenshotToDeviceStorage(
        context: Context,
        bitmap: Bitmap
    ): SavedMediaResult {
        val fileName = buildFileName("png")
        val relativeDir = "${Environment.DIRECTORY_PICTURES}/$IMAGE_SUBFOLDER"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create MediaStore screenshot entry")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw IllegalStateException("Unable to open output stream for screenshot")

                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
                SavedMediaResult(uri = uri, fileName = fileName, displayDirectory = relativeDir)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val picsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_SUBFOLDER
            )
            if (!picsDir.exists()) picsDir.mkdirs()
            val target = File(picsDir, fileName)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf("image/png"),
                null
            )
            SavedMediaResult(
                uri = Uri.fromFile(target),
                fileName = fileName,
                displayDirectory = relativeDir
            )
        }
    }
}
