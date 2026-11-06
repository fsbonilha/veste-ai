package com.vesteai.feature.settings

import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialSettings
import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.credentials.SaveOutcome
import com.vesteai.core.reset.AppDataReset
import com.vesteai.core.result.DataResult
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val credentials = FakeCredentialSettings()
    private val reset = FakeAppDataReset()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(engineName: String = "Mock (offline)") =
        SettingsViewModel(credentials, FakeEngine(engineName), reset)

    @Test
    fun engineNameComesFromTheEngineNotAProviderString() = runTest(dispatcher) {
        val viewModel = viewModel(engineName = "Some Engine")

        advanceUntilIdle()

        assertEquals("Some Engine", viewModel.uiState.value.engineName)
    }

    @Test
    fun startsAbsentWithTheDefaultBaseUrl() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals(CredentialState.Absent, viewModel.uiState.value.credential)
        assertEquals("https://api.pixelapi.dev", viewModel.uiState.value.baseUrlInput)
        assertNull(viewModel.uiState.value.maskedKey)
    }

    @Test
    fun aStoredKeyIsShownOnlyAsAMaskWithItsBaseUrl() = runTest(dispatcher) {
        credentials.current = CredentialState.Present("****3456", "https://staging.test")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("****3456", viewModel.uiState.value.maskedKey)
        assertEquals("https://staging.test", viewModel.uiState.value.baseUrlInput)
    }

    @Test
    fun aMalformedKeyIsRejectedWithoutTouchingStorage() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onApiKeyChanged("nope")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            KeyMessage.RejectedFormat(CredentialFormatError.MISSING_PREFIX),
            viewModel.uiState.value.keyMessage,
        )
        assertEquals(0, credentials.saveCalls)
    }

    @Test
    fun aBlankKeyIsRejectedAsBlank() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            KeyMessage.RejectedFormat(CredentialFormatError.BLANK),
            viewModel.uiState.value.keyMessage,
        )
        assertEquals(0, credentials.saveCalls)
    }

    @Test
    fun saveStoresTheKeyAndClearsTheInput() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onApiKeyChanged("pk_live_abcdef3456")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, credentials.saveCalls)
        assertEquals("", viewModel.uiState.value.apiKeyInput)
        assertNull(viewModel.uiState.value.keyMessage)
    }

    @Test
    fun anUnavailableStoreIsReportedAsStorageUnavailable() = runTest(dispatcher) {
        credentials.saveOutcome = SaveOutcome.StorageUnavailable
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onApiKeyChanged("pk_live_abcdef3456")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(KeyMessage.StorageUnavailable, viewModel.uiState.value.keyMessage)
    }

    @Test
    fun aFailedSaveKeepsTheInputSoTheUserCanRetry() = runTest(dispatcher) {
        credentials.saveOutcome = SaveOutcome.Failed
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onApiKeyChanged("pk_live_abcdef3456")

        viewModel.save()
        advanceUntilIdle()

        assertEquals("pk_live_abcdef3456", viewModel.uiState.value.apiKeyInput)
        assertEquals(KeyMessage.SaveFailed, viewModel.uiState.value.keyMessage)
    }

    @Test
    fun removeKeyErasesTheStoredEntryAndFallsBackToAbsent() = runTest(dispatcher) {
        credentials.current = CredentialState.Present("****3456", "https://api.pixelapi.dev")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.removeKey()
        advanceUntilIdle()

        assertEquals(1, credentials.clearCalls)
        assertEquals(CredentialState.Absent, viewModel.uiState.value.credential)
    }

    @Test
    fun anUnreadableStoreAsksForReentry() = runTest(dispatcher) {
        credentials.current = CredentialState.Unreadable

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsReentry)
    }

    @Test
    fun clearDataAsksForConfirmationBeforeDeleting() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestClearData()
        advanceUntilIdle()

        assertEquals(ClearDataState.Confirming, viewModel.uiState.value.clearData)
        assertEquals(0, reset.calls)
    }

    @Test
    fun cancellingClearDataDeletesNothing() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.requestClearData()

        viewModel.cancelClearData()
        advanceUntilIdle()

        assertEquals(ClearDataState.Idle, viewModel.uiState.value.clearData)
        assertEquals(0, reset.calls)
    }

    @Test
    fun confirmingClearDataReportsSuccess() = runTest(dispatcher) {
        credentials.current = CredentialState.Present("****3456", "https://api.pixelapi.dev")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestClearData()
        viewModel.confirmClearData()
        advanceUntilIdle()

        assertEquals(1, reset.calls)
        assertEquals(ClearDataState.Cleared, viewModel.uiState.value.clearData)
        assertEquals(CredentialState.Absent, viewModel.uiState.value.credential)
    }

    @Test
    fun aFailedClearIsReported() = runTest(dispatcher) {
        reset.result = DataResult.Failure(IOException("disk"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestClearData()
        viewModel.confirmClearData()
        advanceUntilIdle()

        assertEquals(ClearDataState.Failed, viewModel.uiState.value.clearData)
    }

    @Test
    fun theBaseUrlIsSavedWithTheKey() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onApiKeyChanged("pk_live_abcdef3456")
        viewModel.onBaseUrlChanged("https://staging.test")

        viewModel.save()
        advanceUntilIdle()

        assertEquals("https://staging.test", credentials.lastSavedBaseUrl)
    }

    @Test
    fun aStoredBaseUrlFillsTheField() = runTest(dispatcher) {
        credentials.current = CredentialState.Present("****3456", "https://staging.test")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("https://staging.test", viewModel.uiState.value.baseUrlInput)
    }

    @Test
    fun clearingAllDataResetsTheBaseUrlToTheDefault() = runTest(dispatcher) {
        credentials.current = CredentialState.Present("****3456", "https://staging.test")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestClearData()
        viewModel.confirmClearData()
        advanceUntilIdle()

        assertEquals("https://api.pixelapi.dev", viewModel.uiState.value.baseUrlInput)
    }

    @Test
    fun canSaveTracksTheLocalFormatCheck() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onApiKeyChanged("pk_live_abc")
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onApiKeyChanged("pk_live_")
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onApiKeyChanged("sk_live_abc")
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun aSavedKeySurfacesOnlyMaskedInState() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onApiKeyChanged("pk_live_abcdef3456")

        viewModel.save()
        advanceUntilIdle()

        assertEquals("****3456", viewModel.uiState.value.maskedKey)
        assertFalse(viewModel.uiState.value.toString().contains("pk_live_abcdef3456"))
    }
}

private class FakeCredentialSettings : CredentialSettings {
    var current: CredentialState = CredentialState.Absent
    var saveOutcome: SaveOutcome = SaveOutcome.Saved
    var saveCalls = 0
    var clearCalls = 0
    var lastSavedBaseUrl: String? = null

    private val changes = MutableStateFlow(0)

    override fun observe(): Flow<CredentialState> = changes.map { current }

    override suspend fun state(): CredentialState = current

    override suspend fun save(apiKey: String, baseUrl: String): SaveOutcome {
        saveCalls++
        lastSavedBaseUrl = baseUrl
        if (saveOutcome == SaveOutcome.Saved) {
            current = CredentialState.Present("****" + apiKey.trim().takeLast(4), baseUrl)
            changes.value++
        }
        return saveOutcome
    }

    override suspend fun clear() {
        clearCalls++
        current = CredentialState.Absent
        changes.value++
    }
}

private class FakeAppDataReset : AppDataReset {
    var calls = 0
    var result: DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun clearAll(): DataResult<Unit> {
        calls++
        return result
    }
}

private class FakeEngine(override val displayName: String) : VtonEngine {
    override val capabilities = VtonCapabilities(
        supportedCategories = emptySet(),
        supportsTopBottomOutfit = false,
        maxParallelJobs = 1,
        requiredAspect = AspectRequirement.Unconstrained,
    )

    override suspend fun render(request: VtonRequest): DataResult<VtonResult> = error("not used")
}