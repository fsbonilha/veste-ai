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

    /** The files directly inside [kind]'s directory; empty when that directory does not exist. */
    fun listFiles(kind: MediaKind): List<File> =
        File(root, kind.directory).listFiles()?.filter { it.isFile }.orEmpty()

    /** Inverse of [resolve]; null when [file] lies outside this store. */
    fun relativePathOf(file: File): String? {
        val rootPath = root.absoluteFile.path + File.separator
        val filePath = file.absoluteFile.path
        if (!filePath.startsWith(rootPath)) return null
        return filePath.substring(rootPath.length).replace(File.separatorChar, '/')
    }

    /** Removes every media tree this store owns, leaving files it does not manage untouched. */
    fun clearAll() {
        MediaKind.entries.forEach { kind ->
            File(root, kind.directory).deleteRecursively()
        }
    }

    companion object {
        const val BASELINE_PATH = "baseline/baseline.jpg"

        /** Suffix of a file still being written; see [ImageNormalizer]'s atomic write. */
        const val TMP_SUFFIX = ".tmp"
    }
}
