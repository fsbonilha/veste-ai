package com.vesteai.data.vton

import javax.inject.Inject

/**
 * Production [VtonCredentialProvider] (plan §6.4): the single seam through which an engine obtains
 * the user's own key. It reads the encrypted store on every call and never caches the key in a
 * field, so a "Remove key" in Settings takes effect for the next Try On.
 */
class EncryptedVtonCredentialProvider @Inject constructor(
    private val store: CredentialStore,
) : VtonCredentialProvider {
    override suspend fun credentials(): VtonCredentials? = store.read()
}