package com.vesteai.domain.usecase

import com.vesteai.core.media.ImageStore
import com.vesteai.core.media.MediaKind
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.WardrobeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Clock
import javax.inject.Inject

/**
 * Deletes image files that no row references (plan §5). One direction only: a row whose file is
 * missing is left alone, because the user can still delete or re-favourite it and a missing file
 * is a display problem, not a reason to silently drop their data.
 */
class OrphanFileSweepUseCase @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val lookbook: LookbookRepository,
    private val imageStore: ImageStore,
    private val clock: Clock,
) : OrphanFileSweep {

    override suspend fun sweep(): SweepReport =
        try {
            sweepTrees()
        } catch (cancelled: CancellationException) {
            // Cancellation is not a sweep failure: let the scope see it.
            throw cancelled
        } catch (_: Exception) {
            // Startup work: a closed database or an unreadable directory must never crash the app.
            SweepReport(0)
        }

    private suspend fun sweepTrees(): SweepReport {
        val referenced = referencedPaths()
        var deleted = 0
        for (kind in SWEPT_KINDS) {
            for (file in imageStore.listFiles(kind)) {
                currentCoroutineContext().ensureActive()
                if (deleteIfOrphan(file, referenced)) deleted++
            }
        }
        return SweepReport(deleted)
    }

    /**
     * Both stores are read as one-shot snapshots through their observable reads, so the sweep needs
     * no query of its own. If either read fails, the sweep deletes nothing rather than guessing.
     */
    private suspend fun referencedPaths(): Set<String> =
        buildSet {
            wardrobe.observeAll().first().forEach { add(it.imagePath) }
            lookbook.observeAll().first().forEach { add(it.resultImagePath) }
        }

    private fun deleteIfOrphan(file: File, referenced: Set<String>): Boolean {
        val relative = imageStore.relativePathOf(file) ?: return false
        if (relative in referenced) return false
        if (isInFlightTempFile(file)) return false
        return try {
            file.delete()
        } catch (e: SecurityException) {
            false
        }
    }

    /** A temp file is only debris once no write can still be finishing it. */
    private fun isInFlightTempFile(file: File): Boolean =
        file.name.endsWith(ImageStore.TMP_SUFFIX) &&
            clock.millis() - file.lastModified() < TMP_MAX_AGE_MILLIS

    private companion object {
        // baseline/ is excluded on purpose: baseline.jpg is tracked by ProfileRepository, not by a
        // row, so "no row references it" is always true and the sweep would delete it.
        val SWEPT_KINDS = listOf(MediaKind.WARDROBE, MediaKind.LOOK)
        const val TMP_MAX_AGE_MILLIS = 60L * 60L * 1000L
    }
}
