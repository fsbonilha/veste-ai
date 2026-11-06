package com.vesteai.domain.usecase

/** How many image files a sweep removed. */
data class SweepReport(val deletedFiles: Int)

/**
 * Removes image files that no database row points at any more (plan §5), e.g. after a crash
 * between an import and its insert, or an interrupted atomic write.
 */
interface OrphanFileSweep {
    /**
     * Deletes orphaned files under the wardrobe and lookbook trees. Safe to call at any time and
     * from any thread: it never throws, and a sweep that cannot read the rows deletes nothing.
     */
    suspend fun sweep(): SweepReport
}
