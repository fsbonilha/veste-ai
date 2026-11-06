package com.vesteai.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Writes a JPEG whose left half is red and right half is blue, tagged with [orientation]. */
internal fun writeTestJpeg(
    file: File,
    width: Int,
    height: Int,
    orientation: Int = ExifInterface.ORIENTATION_NORMAL,
) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
    bitmap.recycle()
    ExifInterface(file).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
    }
}

internal fun decodeSize(file: File): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    return options.outWidth to options.outHeight
}

/** Coarse colour name, tolerant of JPEG artefacts. */
internal fun colorAt(file: File, x: Int, y: Int): String {
    val bitmap = BitmapFactory.decodeFile(file.path)
    val pixel = bitmap.getPixel(x, y)
    bitmap.recycle()
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    return when {
        r > 200 && g > 200 && b > 200 -> "white"
        r > 180 && g < 80 && b < 80 -> "red"
        b > 180 && r < 80 && g < 80 -> "blue"
        else -> "other($r,$g,$b)"
    }
}
