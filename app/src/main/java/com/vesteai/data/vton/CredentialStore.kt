package com.vesteai.data.vton

import com.vesteai.core.credentials.CredentialSettings
import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.credentials.SaveOutcome
import com.vesteai.core.credentials.validateApiKey
import kotlinx.coroutines.flow.Flow

/**
 * Storage-facing side of BYOK credentials (plan §6.4). [EncryptedCredentialStore] is the only
 * implementation; engines read it through [VtonCredentialProvider] and screens through
 * [CredentialSettings], which masks the key.
 */
interface CredentialStore {
    /** The raw key, for engines only. Null when absent or when the encrypted store is unusable. */
    suspend fun read(): VtonCredentials?

    fun observe(): Flow<CredentialState>

    suspend fun state(): CredentialState

    suspend fun save(apiKey: String, baseUrl: String): SaveOutcome

    /** Erases the stored entry, so [read] returns null and engines fall back to Mock. */
    suspend fun clear()
}

/** Screen-facing adapter: lets Settings observe and change the entry without seeing the key. */
class SettingsCredentialSettings(
    private val store: EncryptedCredentialStore,
) : CredentialSettings {
    override fun observe(): Flow<CredentialState> = store.observe()

    override suspend fun state(): CredentialState = store.state()

    override suspend fun save(apiKey: String, baseUrl: String): SaveOutcome =
        store.save(apiKey, baseUrl)

    override suspend fun clear() = store.clear()
}