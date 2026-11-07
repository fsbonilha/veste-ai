package com.vesteai.data.vton.pixelapi

import com.vesteai.data.vton.VtonCredentialProvider
import com.vesteai.domain.vton.VtonEngine

/**
 * The one place the provider is constructed (plan §6.6). Keeps [PixelApiClient],
 * [PixelApiConfig] and the DTOs internal to this package: swapping providers means replacing
 * this factory and the binding that calls it, and nothing else.
 */
object PixelApiEngineFactory {
    fun create(credentialProvider: VtonCredentialProvider): VtonEngine {
        val config = PixelApiConfig()
        return PixelApiVtonEngine(
            credentialProvider = credentialProvider,
            client = PixelApiClient(config.newHttpClient()),
            config = config,
        )
    }
}
