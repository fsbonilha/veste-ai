package com.vesteai.core.media

import android.net.Uri
import com.vesteai.core.result.DataResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageImporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val dispatcher = UnconfinedTestDispatcher()
    private val store by lazy { ImageStore(File(tmp.root, "files")) }
    private val importer by lazy { ImageImporter(context, store, ImageNormalizer(), dispatcher) }

    @Test
    fun fileImportForBaselineWritesFixedPath() = runTest(dispatcher) {
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        val result = importer.import(capture, MediaKind.BASELINE)

        assertEquals(DataResult.Success(ImportedImage("baseline/baseline.jpg", 600, 800)), result)
        assertEquals(600 to 800, decodeSize(store.resolve("baseline/baseline.jpg")))
    }

    @Test
    fun contentUriImportIsNormalizedIntoWardrobeAndStagingIsCleanedUp() = runTest(dispatcher) {
        val picked = File(tmp.root, "picked.jpg").also { writeTestJpeg(it, 1500, 2000) }
        val uri = Uri.parse("content://media/picker/0/42")
        shadowOf(context.contentResolver).registerInputStream(uri, picked.inputStream())

        val imported = (importer.import(uri, MediaKind.WARDROBE) as DataResult.Success).value

        assertTrue(imported.relativePath, imported.relativePath.startsWith("wardrobe/"))
        assertEquals(768 to 1024, decodeSize(store.resolve(imported.relativePath)))
        assertEquals(emptyList<String>(), context.cacheDir.list()!!.filter { it.startsWith("import-") })
    }

    @Test
    fun tooSmallUriImportFailsWithTypedError() = runTest(dispatcher) {
        val picked = File(tmp.root, "tiny.jpg").also { writeTestJpeg(it, 200, 200) }
        val uri = Uri.parse("content://media/picker/0/7")
        shadowOf(context.contentResolver).registerInputStream(uri, picked.inputStream())

        val error = (importer.import(uri, MediaKind.WARDROBE) as DataResult.Failure).error

        assertTrue(error is ImageImportException.TooSmall)
    }
}
