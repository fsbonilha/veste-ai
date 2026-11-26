package com.vesteai.core.di

import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.result.DataResult
import com.vesteai.data.vton.CredentialStore
import com.vesteai.data.vton.VtonCredentialProvider
import com.vesteai.data.vton.mock.CanvasMockCompositor
import com.vesteai.data.vton.mock.MockVtonEngine
import com.vesteai.data.vton.pixelapi.PixelApiEngineFactory
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VtonEngineModule {

    /**
     * Swapping providers is this call plus [PixelApiEngineFactory] (plan §6.6). With no key saved
     * the app runs on [MockVtonEngine], so Try On works before setup and again after the key is
     * removed.
     */
    @Provides
    @Singleton
    fun provideVtonEngine(
        credentialProvider: VtonCredentialProvider,
        credentialStore: CredentialStore,
        httpLogging: HttpLogging,
    ): VtonEngine = SelectingVtonEngine(
        mock = MockVtonEngine(dispatcher = Dispatchers.Default, compositor = CanvasMockCompositor()),
        remote = PixelApiEngineFactory.create(credentialProvider, httpLogging.interceptors()),
        credentialStates = credentialStore.observe(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}

/**
 * Chooses the engine from whether a key is stored, never from a provider-name string (plan §6.6).
 *
 * It starts on [mock] and switches once the credential store reports its state, so a provider
 * outage or a rejected key never leaves the user without Try On. The window before the first
 * emission is one frame of the store read; reports offline capabilities, which is the safe lie.
 */
internal class SelectingVtonEngine(
    private val mock: VtonEngine,
    private val remote: VtonEngine,
    credentialStates: Flow<CredentialState>,
    scope: CoroutineScope,
) : VtonEngine {

    @Volatile
    private var configured: Boolean = false

    init {
        scope.launch {
            credentialStates.collect { configured = it is CredentialState.Present }
        }
    }

    private val active: VtonEngine get() = if (configured) remote else mock

    override val displayName: String get() = active.displayName

    override val capabilities: VtonCapabilities get() = active.capabilities

    override suspend fun render(request: VtonRequest): DataResult<VtonResult> = active.render(request)
}
