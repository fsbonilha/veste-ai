package com.vesteai.core.media

import androidx.exifinterface.media.ExifInterface
import com.vesteai.core.result.DataResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageNormalizerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val normalizer = ImageNormalizer()
    private val source by lazy { File(tmp.root, "source.jpg") }
    private val target by lazy { File(tmp.root, "out/target.jpg") }
    private val tmpFile by lazy { File(target.path + ImageNormalizer.TMP_SUFFIX) }

    @Test
    fun exifRotate90IsAppliedSoOutputIsUpright() {
        writeTestJpeg(source, 800, 600, ExifInterface.ORIENTATION_ROTATE_90)

        val result = normalizer.normalize(source, target)

        assertEquals(DataResult.Success(NormalizedImage(600, 800)), result)
        assertEquals(600 to 800, decodeSize(target))
        assertEquals("red", colorAt(target, 300, 100))
        assertEquals("blue", colorAt(target, 300, 700))
    }

    @Test
    fun exifRotate270IsAppliedSoOutputIsUpright() {
        writeTestJpeg(source, 800, 600, ExifInterface.ORIENTATION_ROTATE_270)

        normalizer.normalize(source, target)

        assertEquals("blue", colorAt(target, 300, 100))
        assertEquals("red", colorAt(target, 300, 700))
    }

    @Test
    fun landscapeIsLetterboxedOntoPortraitFrameWithoutStretching() {
        writeTestJpeg(source, 800, 600)

        val result = normalizer.normalize(source, target)

        assertEquals(DataResult.Success(NormalizedImage(768, 1024)), result)
        assertEquals("white", colorAt(target, 384, 20))
        assertEquals("red", colorAt(target, 100, 512))
        assertEquals("blue", colorAt(target, 668, 512))
        assertEquals("white", colorAt(target, 384, 1004))
    }

    @Test
    fun tallNarrowImageIsPaddedSidewaysToPortraitFrame() {
        writeTestJpeg(source, 300, 1200)

        val result = normalizer.normalize(source, target)

        assertEquals(DataResult.Success(NormalizedImage(768, 1024)), result)
        assertEquals("white", colorAt(target, 20, 512))
        assertEquals("white", colorAt(target, 748, 512))
    }

    @Test
    fun longEdgeIsDownscaledTo1024() {
        writeTestJpeg(source, 1500, 2000)

        val result = normalizer.normalize(source, target)

        assertEquals(DataResult.Success(NormalizedImage(768, 1024)), result)
        assertEquals(768 to 1024, decodeSize(target))
    }

    @Test
    fun smallPortraitImageIsNotUpscaled() {
        writeTestJpeg(source, 300, 400)

        val result = normalizer.normalize(source, target)

        assertEquals(DataResult.Success(NormalizedImage(300, 400)), result)
    }

    @Test
    fun imageBelowMinimumIsRejectedWithItsDimensions() {
        writeTestJpeg(source, 299, 400)

        val error = (normalizer.normalize(source, target) as DataResult.Failure).error

        val tooSmall = error as ImageImportException.TooSmall
        assertEquals(299 to 400, tooSmall.width to tooSmall.height)
        assertFalse(target.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun minimumSizeIsCheckedOnUprightDimensions() {
        writeTestJpeg(source, 500, 350, ExifInterface.ORIENTATION_ROTATE_90)

        val result = normalizer.normalize(source, target)

        assertTrue(result is DataResult.Success)
    }

    @Test
    fun nonImageIsRejectedAsUndecodable() {
        source.writeText("not an image")

        val error = (normalizer.normalize(source, target) as DataResult.Failure).error

        assertTrue(error is ImageImportException.Undecodable)
        assertFalse(target.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun failedNormalizeLeavesExistingTargetUntouched() {
        target.parentFile!!.mkdirs()
        target.writeBytes(byteArrayOf(1, 2, 3))
        writeTestJpeg(source, 100, 100)

        normalizer.normalize(source, target)

        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun partialTempFileFromInterruptedWriteIsReplacedAndRemoved() {
        target.parentFile!!.mkdirs()
        tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        writeTestJpeg(source, 600, 800)

        normalizer.normalize(source, target)

        assertEquals(600 to 800, decodeSize(target))
        assertFalse(tmpFile.exists())
    }

    @Test
    fun successReplacesExistingTarget() {
        writeTestJpeg(File(tmp.root, "old.jpg"), 300, 400)
        normalizer.normalize(File(tmp.root, "old.jpg"), target)
        writeTestJpeg(source, 600, 800)

        normalizer.normalize(source, target)

        assertEquals(600 to 800, decodeSize(target))
    }
}
