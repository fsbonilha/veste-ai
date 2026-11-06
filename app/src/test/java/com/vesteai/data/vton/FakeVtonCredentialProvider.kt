package com.vesteai.data.vton

class FakeVtonCredentialProvider(
    var stored: VtonCredentials? = null,
) : VtonCredentialProvider {
    override suspend fun credentials(): VtonCredentials? = stored
}
