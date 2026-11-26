package com.vesteai.core.di

import com.vesteai.core.result.DataResult
import com.vesteai.core.credentials.CredentialState
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectingVtonEngineTest {

    private class NamedEngine(override val displayName: String) : VtonEngine {
        var renders = 0

        override val capabilities: VtonCapabilities = VtonCapabilities(
            supportedCategories = GarmentCategory.entries.toSet(),
            supportsTopBottomOutfit = true,
            maxParallelJobs = 1,
            requiredAspect = AspectRequirement.Unconstrained,
        )

        override suspend fun submit(request: VtonRequest): DataResult<String> =
            DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

        override suspend fun poll(jobId: String): DataResult<VtonPollResult?> =
            DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

        override suspend fun render(request: VtonRequest): DataResult<VtonResult> {
            renders++
            return DataResult.Success(VtonResult(byteArrayOf(1)))
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val states = MutableStateFlow<CredentialState>(CredentialState.Absent)

    private fun engine() = SelectingVtonEngine(
        mock = NamedEngine("Mock"),
        remote = NamedEngine("PixelAPI"),
        credentialStates = states,
        scope = scope,
    )

    @Test
    fun withoutAStoredKeyTheMockEngineIsUsed() = runTest(dispatcher) {
        val engine = engine()
        advanceUntilIdle()

        assertEquals("Mock", engine.displayName)
    }

    @Test
    fun withAStoredKeyTheRemoteEngineIsUsed() = runTest(dispatcher) {
        states.value = CredentialState.Present(maskedKey = "****1234", baseUrl = "")
        val engine = engine()
        advanceUntilIdle()

        assertEquals("PixelAPI", engine.displayName)
    }

    @Test
    fun beforeTheStoreReportsItsStateTheEngineIsMock() {
        // Reading the credential store is suspending, so before its first emission the engine
        // answers Mock. That is the safe direction: the UI offers offline try-on rather than
        // promising a provider it cannot reach.
        val engine = engine()

        assertEquals("Mock", engine.displayName)
    }

    @Test
    fun savingAKeySwitchesToTheRemoteEngine() = runTest(dispatcher) {
        val engine = engine()
        advanceUntilIdle()

        states.value = CredentialState.Present(maskedKey = "****1234", baseUrl = "")
        advanceUntilIdle()

        assertEquals("PixelAPI", engine.displayName)
    }

    @Test
    fun removingTheKeyFallsBackToMock() = runTest(dispatcher) {
        val engine = engine()
        advanceUntilIdle()

        states.value = CredentialState.Absent
        advanceUntilIdle()

        assertEquals("Mock", engine.displayName)
    }

    @Test
    fun anUnreadableKeyIsTreatedAsAbsent() = runTest(dispatcher) {
        val engine = engine()
        advanceUntilIdle()

        states.value = CredentialState.Unreadable
        advanceUntilIdle()

        assertEquals("Mock", engine.displayName)
    }

    @Test
    fun renderGoesToTheSelectedEngine() = runTest(dispatcher) {
        states.value = CredentialState.Present(maskedKey = "****1234", baseUrl = "")
        val mock = NamedEngine("Mock")
        val remote = NamedEngine("PixelAPI")
        val engine = SelectingVtonEngine(
            mock = mock,
            remote = remote,
            credentialStates = states,
            scope = scope,
        )
        advanceUntilIdle()

        engine.render(VtonRequest(baselineImage = byteArrayOf(0), garments = emptyMap()))
        advanceUntilIdle()

        assertEquals(1, remote.renders)
        assertEquals(0, mock.renders)
    }
}
