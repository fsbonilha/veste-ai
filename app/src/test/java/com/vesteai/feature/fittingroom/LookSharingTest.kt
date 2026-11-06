package com.vesteai.feature.fittingroom

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * A single test method on purpose: [FileProvider] caches its path strategy per authority for
 * the life of the JVM, and Robolectric hands each test method a fresh data directory, so a
 * second method would resolve the first method's (now deleted) root.
 */
@RunWith(RobolectricTestRunner::class)
class LookSharingTest {

    private val context: Application = RuntimeEnvironment.getApplication()

    private fun file(vararg segments: String): File {
        val file = segments.fold(context.filesDir) { dir, name -> File(dir, name) }
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return file
    }

    @Test
    fun sharingSendsTheResultThroughTheManifestFileProvider() {
        val look = file("looks", "result.jpg")

        LookSharing(context)(look)

        val chooser = shadowOf(context).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)

        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("image/jpeg", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertEquals(context.packageName + ".fileprovider", uri!!.authority)
        assertEquals("content", uri.scheme)
        assertTrue(uri.path!!.startsWith("/looks/"))

        val sharesBaseline = runCatching {
            FileProvider.getUriForFile(
                context,
                context.fileProviderAuthority(),
                file("baseline", "baseline.jpg"),
            )
        }
        assertTrue(sharesBaseline.isFailure)
    }
}
