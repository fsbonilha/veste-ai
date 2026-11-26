package com.vesteai.data.vton.pixelapi

import com.vesteai.data.vton.VtonCredentialProvider
import com.vesteai.domain.vton.VtonEngine
import okhttp3.Interceptor

/**
 * The one place the provider is constructed (plan §6.6). Keeps [PixelApiClient],
 * [PixelApiConfig] and the DTOs internal to this package: swapping providers means replacing
 * this factory and the binding that calls it, and nothing else.
 */
object PixelApiEngineFactory {
    fun create(
        credentialProvider: VtonCredentialProvider,
        interceptors: List<Interceptor> = emptyList(),
    ): VtonEngine {
        val config = PixelApiConfig()
        return PixelApiVtonEngine(
            credentialProvider = credentialProvider,
            client = PixelApiClient(config.newHttpClient(interceptors)),
            config = config,
        )
    }
}
