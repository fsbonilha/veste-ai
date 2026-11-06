package com.vesteai.data.vton.mock

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.vesteai.domain.model.GarmentCategory
import java.io.ByteArrayOutputStream

fun interface MockCompositor {
    /** Returns PNG bytes, or null if the baseline or any garment cannot be decoded. */
    fun composite(baseline: ByteArray, garments: Map<GarmentCategory, ByteArray>): ByteArray?
}

/** Draws each garment thumbnail into a fixed per-category band over the baseline. */
class CanvasMockCompositor : MockCompositor {
    override fun composite(baseline: ByteArray, garments: Map<GarmentCategory, ByteArray>): ByteArray? {
        val base = BitmapFactory.decodeByteArray(baseline, 0, baseline.size) ?: return null
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        base.recycle()
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = OVERLAY_ALPHA }
        for (category in GarmentCategory.entries) {
            val bytes = garments[category] ?: continue
            val thumb = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (thumb == null) {
                output.recycle()
                return null
            }
            canvas.drawBitmap(thumb, null, band(category, output.width, output.height), paint)
            thumb.recycle()
        }
        val png = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, png)
        output.recycle()
        return png.toByteArray()
    }

    private fun band(category: GarmentCategory, width: Int, height: Int): Rect {
        val (top, bottom) = when (category) {
            GarmentCategory.HEADWEAR -> 0.00f to 0.15f
            GarmentCategory.TOP -> 0.15f to 0.50f
            GarmentCategory.BOTTOM -> 0.50f to 0.85f
            GarmentCategory.FULL_BODY -> 0.15f to 0.85f
            GarmentCategory.FOOTWEAR -> 0.85f to 1.00f
        }
        val left = width / 4
        return Rect(left, (height * top).toInt(), width - left, (height * bottom).toInt())
    }

    private companion object {
        const val OVERLAY_ALPHA = 200
        const val PNG_QUALITY = 100
    }
}
