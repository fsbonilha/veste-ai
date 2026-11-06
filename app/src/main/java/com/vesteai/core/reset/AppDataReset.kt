package com.vesteai.core.reset

import com.vesteai.core.result.DataResult

/**
 * Wipes everything this app owns on the device: wardrobe and lookbook rows, their image files, the
 * profile flags, and the stored BYOK credentials. Settings owns the confirmation; this only does
 * the work, and reports a failure instead of leaving the user guessing.
 */
interface AppDataReset {
    suspend fun clearAll(): DataResult<Unit>
}