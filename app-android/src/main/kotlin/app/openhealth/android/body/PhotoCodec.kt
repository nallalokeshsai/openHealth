@file:Suppress("MagicNumber")

package app.openhealth.android.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Progress photos (`DESIGN.md` §9.4): read a picked image, downscale it, encode a JPEG and
 * a 256 px thumbnail, and decode them again for the gallery. The encoded bytes go into
 * the SQLCipher database, so no photo is ever written to shared storage or the media
 * gallery, and the platform backup is excluded (`pr.md` §6.25, `data_extraction_rules`).
 *
 * The original file name and metadata are not kept: EXIF (including any location tag) is
 * dropped by re-encoding, and the person's own caption is the only text stored.
 */
@Suppress("TooManyFunctions")
object PhotoCodec {
    const val MAX_SOURCE_BYTES = 30 * 1024 * 1024
    private const val MAX_DIMENSION = 1600
    private const val THUMB_DIMENSION = 256
    private const val JPEG_QUALITY = 85
    private const val THUMB_QUALITY = 75

    data class Prepared(
        val bytes: ByteArray,
        val thumbnail: ByteArray,
        val width: Int,
        val height: Int,
        val sha256: String,
    )

    fun read(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_SOURCE_BYTES) return@use null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }.getOrNull()

    fun prepare(input: ByteArray): Prepared? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size, options)
            ?: return null
        val rotated = rotate(decoded, orientation(input))
        val scaled = scaleDown(rotated, MAX_DIMENSION)
        val full = encode(scaled, JPEG_QUALITY)
        val thumb = encode(scaleDown(scaled, THUMB_DIMENSION), THUMB_QUALITY)
        Prepared(
            bytes = full,
            thumbnail = thumb,
            width = scaled.width,
            height = scaled.height,
            sha256 = sha256(full),
        )
    }.getOrNull()

    fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64(text: String?): ByteArray? = runCatching {
        if (text.isNullOrBlank()) null else Base64.decode(text, Base64.NO_WRAP)
    }.getOrNull()

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun sampleFor(width: Int, height: Int, max: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= max || height / (sample * 2) >= max) sample *= 2
        return sample
    }

    private fun orientation(input: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(input))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val ratio = max.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }
}
