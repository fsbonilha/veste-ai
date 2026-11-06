package com.vesteai.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.vesteai.core.result.DataResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

data class NormalizedImage(val width: Int, val height: Int)

/**
 * Produces the single canonical form every imported image is stored in: EXIF-upright,
 * letterboxed (never stretched or cropped) onto a 3:4 portrait frame, long edge at most
 * [MAX_LONG_EDGE], JPEG q[JPEG_QUALITY]. Padding rather than cropping keeps the whole
 * garment / person in frame. Blocking; call from an IO dispatcher.
 */
class ImageNormalizer @Inject constructor() {

    fun normalize(source: File, target: File): DataResult<NormalizedImage> {
        val tmp = File(target.path + TMP_SUFFIX)
        return try {
            val bitmap = render(source)
            try {
                writeAtomically(bitmap, tmp, target)
                DataResult.Success(NormalizedImage(bitmap.width, bitmap.height))
            } finally {
                bitmap.recycle()
            }
        } catch (e: ImageImportException) {
            tmp.delete()
            DataResult.Failure(e)
        } catch (e: IOException) {
            tmp.delete()
            DataResult.Failure(ImageImportException.Unreadable(e))
        }
    }

    private fun render(source: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageImportException.Undecodable()

        val orientation = ExifInterface(source)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val swapsAxes = orientation in AXIS_SWAPPING_ORIENTATIONS
        val width = if (swapsAxes) bounds.outHeight else bounds.outWidth
        val height = if (swapsAxes) bounds.outWidth else bounds.outHeight
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            throw ImageImportException.TooSmall(width, height)
        }

        val (frameWidth, frameHeight) = portraitFrame(width, height)
        val outHeight = min(frameHeight, MAX_LONG_EDGE)
        val outWidth = (frameWidth.toDouble() * outHeight / frameHeight).roundToInt()
        val scale = outHeight.toFloat() / frameHeight

        val decoded = BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(scale) },
        ) ?: throw ImageImportException.Undecodable()

        try {
            val decodedWidth = if (swapsAxes) decoded.height else decoded.width
            val decodedHeight = if (swapsAxes) decoded.width else decoded.height
            val contentWidth = width * scale
            val contentHeight = height * scale
            val matrix = orientationMatrix(orientation, decoded.width, decoded.height).apply {
                postScale(contentWidth / decodedWidth, contentHeight / decodedHeight)
                postTranslate((outWidth - contentWidth) / 2f, (outHeight - contentHeight) / 2f)
            }
            val output = createBitmap(outWidth, outHeight)
            Canvas(output).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return output
        } finally {
            decoded.recycle()
        }
    }

    private fun writeAtomically(bitmap: Bitmap, tmp: File, target: File) {
        val dir = target.absoluteFile.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Cannot create directory ${dir.path}")
        }
        FileOutputStream(tmp).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                throw IOException("JPEG encoding failed")
            }
            out.fd.sync()
        }
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Smallest frame with a 3:4 (w:h) ratio that contains a [width]x[height] image. */
    private fun portraitFrame(width: Int, height: Int): Pair<Int, Int> =
        if (width.toLong() * RATIO_HEIGHT > height.toLong() * RATIO_WIDTH) {
            width to ceilDiv(width.toLong() * RATIO_HEIGHT, RATIO_WIDTH)
        } else {
            ceilDiv(height.toLong() * RATIO_WIDTH, RATIO_HEIGHT) to height
        }

    private fun ceilDiv(numerator: Long, denominator: Int): Int =
        ((numerator + denominator - 1) / denominator).toInt()

    private fun sampleSizeFor(scale: Float): Int {
        var sample = 1
        while (sample * 2 * scale <= 1f) sample *= 2
        return sample
    }

    /** Maps raw bitmap pixels to upright pixels, with the result anchored at the origin. */
    private fun orientationMatrix(orientation: Int, w: Int, h: Int): Matrix {
        val wf = w.toFloat()
        val hf = h.toFloat()
        // Affine rows [a, b, tx, c, d, ty]: x' = a*x + b*y + tx, y' = c*x + d*y + ty.
        val affine = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> floatArrayOf(-1f, 0f, wf, 0f, 1f, 0f)
            ExifInterface.ORIENTATION_ROTATE_180 -> floatArrayOf(-1f, 0f, wf, 0f, -1f, hf)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> floatArrayOf(1f, 0f, 0f, 0f, -1f, hf)
            ExifInterface.ORIENTATION_TRANSPOSE -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f)
            ExifInterface.ORIENTATION_ROTATE_90 -> floatArrayOf(0f, -1f, hf, 1f, 0f, 0f)
            ExifInterface.ORIENTATION_TRANSVERSE -> floatArrayOf(0f, -1f, hf, -1f, 0f, wf)
            ExifInterface.ORIENTATION_ROTATE_270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, wf)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        }
        return Matrix().apply { setValues(affine + floatArrayOf(0f, 0f, 1f)) }
    }

    companion object {
        const val MIN_WIDTH = 300
        const val MIN_HEIGHT = 400
        const val MAX_LONG_EDGE = 1024
        const val JPEG_QUALITY = 85
        const val TMP_SUFFIX = ".tmp"
        private const val RATIO_WIDTH = 3
        private const val RATIO_HEIGHT = 4
        private val AXIS_SWAPPING_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}
