package am.noz.caelum.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedImageResult(
    val file: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Int,
    val mimeType: String = "image/jpeg",
)

object ImageCompressor {
    const val POST_MAX_DIMENSION = 4000
    const val POST_MAX_BYTES = 2_000_000
    const val THUMB_MAX_DIMENSION = 2000
    const val THUMB_MAX_BYTES = 1_000_000

    suspend fun compressUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = POST_MAX_DIMENSION,
        maxBytes: Int = POST_MAX_BYTES,
    ): CompressedImageResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        fun open() = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open image URI: $uri")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions" }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val orientation = open().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = open().use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: throw IllegalStateException("Failed to decode bitmap")

        bitmap = applyExifOrientation(bitmap, orientation)
        bitmap = flattenAlphaToWhite(bitmap)

        // Fit inside maxDimension
        val edge = max(bitmap.width, bitmap.height)
        if (edge > maxDimension) {
            val ratio = maxDimension.toFloat() / edge.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        var working = bitmap
        var encodedBytes = ByteArray(0)
        try {
            for (attempt in 0 until 5) {
                encodedBytes = binarySearchJpeg(working, maxBytes)
                if (encodedBytes.size <= maxBytes) break
                val nextW = (working.width * 0.8f).roundToInt().coerceAtLeast(1)
                val nextH = (working.height * 0.8f).roundToInt().coerceAtLeast(1)
                val shrunk = Bitmap.createScaledBitmap(working, nextW, nextH, true)
                if (shrunk !== working) working.recycle()
                working = shrunk
            }

            val outDir = File(context.cacheDir, "ImageCompressor").apply { mkdirs() }
            val outFile = File.createTempFile("img_", ".jpg", outDir)
            FileOutputStream(outFile).use { it.write(encodedBytes) }

            CompressedImageResult(
                file = outFile,
                width = working.width,
                height = working.height,
                sizeBytes = encodedBytes.size,
            )
        } finally {
            working.recycle()
        }
    }

    private fun binarySearchJpeg(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var low = 25
        var high = 100
        var best: ByteArray? = null
        val baos = ByteArrayOutputStream()

        while (low <= high) {
            val mid = (low + high) / 2
            baos.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, mid, baos)
            val candidate = baos.toByteArray()
            if (candidate.size <= maxBytes) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (best != null) return best
        baos.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos)
        return baos.toByteArray()
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return bitmap
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun flattenAlphaToWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return flattened
    }
}
