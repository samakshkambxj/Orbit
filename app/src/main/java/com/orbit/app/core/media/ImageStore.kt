package com.orbit.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies picked images into app storage, downscaling them so provider requests
 * stay small. Files are owned by the app and can be deleted with the message.
 */
class ImageStore(private val context: Context) {
    suspend fun save(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not open the selected image.")
            require(bytes.size <= MAX_INPUT_BYTES) { "That image is too large. Pick one under 25 MB." }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not a supported image." }

            val sample = sampleSize(bounds.outWidth, bounds.outHeight)
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: error("Could not decode the selected image.")

            val scaled = scaleDown(decoded)
            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            file.absolutePath
        }
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val MAX_DIMENSION = 1568
        const val JPEG_QUALITY = 85
        const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    }
}
