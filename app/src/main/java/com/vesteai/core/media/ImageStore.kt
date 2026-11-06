package com.vesteai.core.media

import java.io.File
import java.util.UUID

enum class MediaKind(val directory: String) {
    BASELINE("baseline"),
    WARDROBE("wardrobe"),
    LOOK("looks"),
}

/**
 * Owns the app-private media tree. The database stores only paths relative to [root],
 * because the absolute data dir can change across installs and restores.
 */
class ImageStore(private val root: File) {

    fun newRelativePath(kind: MediaKind): String = when (kind) {
        MediaKind.BASELINE -> BASELINE_PATH
        MediaKind.WARDROBE, MediaKind.LOOK -> "${kind.directory}/${UUID.randomUUID()}.jpg"
    }

    fun resolve(relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute) {
            "Expected a relative media path, got '$relative'"
        }
        val file = File(root, relative)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            "Media path escapes the media root: '$relative'"
        }
        return file
    }

    fun delete(relative: String): Boolean = resolve(relative).delete()

    companion object {
        const val BASELINE_PATH = "baseline/baseline.jpg"
    }
}
