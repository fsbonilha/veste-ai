package com.vesteai.data.repository

import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.media.ImageStore
import com.vesteai.core.reset.AppDataReset
import com.vesteai.core.result.DataResult
import com.vesteai.data.db.VesteDatabase
import com.vesteai.data.vton.CredentialStore
import com.vesteai.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.sql.SQLException
import javax.inject.Inject

/**
 * Clears the app's own data from Settings: rows, image files, profile flags, then the stored key.
 * Media and flags are cleared only after the rows are gone, so a failure cannot leave the database
 * pointing at images that no longer exist.
 */
class RoomAppDataReset @Inject constructor(
    private val database: VesteDatabase,
    private val imageStore: ImageStore,
    private val profileRepository: ProfileRepository,
    private val credentialStore: CredentialStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppDataReset {

    override suspend fun clearAll(): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            database.clearAllTables()
        } catch (e: SQLException) {
            return@withContext DataResult.Failure(e)
        }
        try {
            imageStore.clearAll()
            profileRepository.reset()
        } catch (e: IOException) {
            return@withContext DataResult.Failure(e)
        }
        credentialStore.clear()
        DataResult.Success(Unit)
    }
}