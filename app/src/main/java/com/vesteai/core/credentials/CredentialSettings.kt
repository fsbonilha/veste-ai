package com.vesteai.core.credentials

import kotlinx.coroutines.flow.Flow

/**
 * The screen-facing side of BYOK credentials (plan §6.4). It can accept a key and report a masked
 * form of it, but never hands the key back: only [com.vesteai.data.vton.VtonCredentialProvider]
 * can read it, and only engines may depend on that.
 *
 * The key and its base URL are one entry and are saved together, so there is no window in which a
 * half-configured provider could be used by an engine.
 */
interface CredentialSettings {
    /** Emits on every save/clear so the screen never shows a stale mask. */
    fun observe(): Flow<CredentialState>

    suspend fun state(): CredentialState

    /** Validates [apiKey] through [validateApiKey], then persists it with [baseUrl]. Never calls the network. */
    suspend fun save(apiKey: String, baseUrl: String): SaveOutcome

    /** Erases the stored entry; engines then fall back to the local Mock engine. */
    suspend fun clear()
}

sealed interface CredentialState {
    data object Absent : CredentialState

    /** [maskedKey] is the only form of the key a screen ever sees, e.g. `****1234`. */
    data class Present(val maskedKey: String, val baseUrl: String) : CredentialState

    /**
     * A key was stored and can no longer be read: the keyset was corrupt or gone and has been wiped.
     * Engines see no key; Settings must ask for it again (plan §6.4).
     */
    data object Unreadable : CredentialState
}

sealed interface SaveOutcome {
    data object Saved : SaveOutcome

    data class InvalidFormat(val error: CredentialFormatError) : SaveOutcome

    /** The encrypted store could not be opened, so nothing was written. */
    data object StorageUnavailable : SaveOutcome

    data object Failed : SaveOutcome
}

enum class CredentialFormatError { BLANK, MISSING_PREFIX }

/** PixelAPI's documented key prefix (plan §6.5); the only thing a local check can verify. */
const val API_KEY_PREFIX = "pk_live_"

/**
 * Local-only key format check (plan §6.4). PixelAPI has no zero-cost health route and a real
 * validation call costs 32 credits, so saving must never touch the network: this checks the shape
 * only, and the first Try On is the source of truth for whether the key is valid.
 *
 * Returns null when [apiKey] is acceptable.
 */
fun validateApiKey(apiKey: String, requiredPrefix: String = API_KEY_PREFIX): CredentialFormatError? =
    when {
        apiKey.isBlank() -> CredentialFormatError.BLANK
        !apiKey.trim().startsWith(requiredPrefix) -> CredentialFormatError.MISSING_PREFIX
        else -> null
    }

/** `pk_live_abc123def456` -> `****f456`. Never returns more than the last four characters. */
fun maskApiKey(apiKey: String): String {
    val trimmed = apiKey.trim()
    return MASK_PREFIX + trimmed.takeLast(MASK_SUFFIX_LENGTH)
}

private const val MASK_PREFIX = "****"
private const val MASK_SUFFIX_LENGTH = 4