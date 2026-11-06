package com.vesteai.data.vton

/**
 * The only route to user-supplied (BYOK) provider credentials, plan §6.4.
 * Lives in `data` so `domain` and `feature` never see the raw key.
 */
interface VtonCredentialProvider {
    /** Returns null when no key has been saved; engines map that to `VtonError.NotConfigured`. */
    suspend fun credentials(): VtonCredentials?
}

class VtonCredentials(
    val apiKey: String,
    val baseUrl: String,
) {
    override fun toString(): String = "VtonCredentials(apiKey=••••, baseUrl=$baseUrl)"
}
