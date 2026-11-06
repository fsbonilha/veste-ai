package com.vesteai.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { ImageStore(tmp.root) }

    @Test
    fun resolveAnchorsRelativePathUnderRoot() {
        assertEquals(File(tmp.root, "wardrobe/a.jpg"), store.resolve("wardrobe/a.jpg"))
    }

    @Test
    fun resolveRejectsAbsolutePath() {
        assertThrows(IllegalArgumentException::class.java) { store.resolve("/etc/passwd") }
    }

    @Test
    fun resolveRejectsPathEscapingRoot() {
        assertThrows(IllegalArgumentException::class.java) { store.resolve("../outside.jpg") }
    }

    @Test
    fun baselineHasFixedPath() {
        assertEquals("baseline/baseline.jpg", store.newRelativePath(MediaKind.BASELINE))
    }

    @Test
    fun wardrobeAndLookPathsAreUniqueJpegsInTheirDirectories() {
        val wardrobe = store.newRelativePath(MediaKind.WARDROBE)
        val look = store.newRelativePath(MediaKind.LOOK)

        assertTrue(wardrobe, Regex("wardrobe/[0-9a-f-]{36}\\.jpg").matches(wardrobe))
        assertTrue(look, Regex("looks/[0-9a-f-]{36}\\.jpg").matches(look))
    }
}
