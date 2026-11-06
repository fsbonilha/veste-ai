package com.vesteai.core.media

import android.content.Context
import android.net.Uri
import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.result.DataResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ImportedImage(val relativePath: String, val width: Int, val height: Int)

/** Single import path for camera captures (files) and photo-picker results (content Uris). */
class ImageImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStore: ImageStore,
    private val normalizer: ImageNormalizer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun import(source: File, kind: MediaKind): DataResult<ImportedImage> =
        withContext(ioDispatcher) { normalizeInto(source, kind) }

    suspend fun import(source: Uri, kind: MediaKind): DataResult<ImportedImage> =
        withContext(ioDispatcher) {
            val staging = try {
                File.createTempFile("import-", ".img", context.cacheDir)
            } catch (e: IOException) {
                return@withContext DataResult.Failure(ImageImportException.Unreadable(e))
            }
            try {
                copy(source, staging)
                normalizeInto(staging, kind)
            } catch (e: IOException) {
                DataResult.Failure(ImageImportException.Unreadable(e))
            } catch (e: SecurityException) {
                DataResult.Failure(ImageImportException.Unreadable(e))
            } finally {
                staging.delete()
            }
        }

    private fun copy(source: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Content resolver returned no stream")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }
    }

    private fun normalizeInto(source: File, kind: MediaKind): DataResult<ImportedImage> {
        val relativePath = imageStore.newRelativePath(kind)
        return when (val result = normalizer.normalize(source, imageStore.resolve(relativePath))) {
            is DataResult.Success ->
                DataResult.Success(ImportedImage(relativePath, result.value.width, result.value.height))
            is DataResult.Failure -> result
        }
    }
}
