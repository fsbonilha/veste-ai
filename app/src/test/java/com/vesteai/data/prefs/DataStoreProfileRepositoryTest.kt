package com.vesteai.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreProfileRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val file: File by lazy { tmp.newFile("profile.preferences_pb") }
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    /** DataStore allows one live instance per file, so each call closes the previous scope first. */
    private fun repositoryOnFile(): DataStoreProfileRepository {
        scopes.forEach { it.cancel() }
        scopes.clear()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        scopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreProfileRepository(dataStore)
    }

    @Test
    fun onboardingIsIncompleteOnFreshInstall() = runTest(dispatcher) {
        assertFalse(repositoryOnFile().observeOnboardingComplete().first())
    }

    @Test
    fun baselineIsAbsentOnFreshInstall() = runTest(dispatcher) {
        assertFalse(repositoryOnFile().observeBaselinePresent().first())
    }

    @Test
    fun completedOnboardingSurvivesRepositoryRecreation() = runTest(dispatcher) {
        repositoryOnFile().completeOnboarding()

        assertTrue(repositoryOnFile().observeOnboardingComplete().first())
    }

    @Test
    fun savedBaselineSurvivesRepositoryRecreation() = runTest(dispatcher) {
        repositoryOnFile().markBaselineSaved(savedAtMillis = 1_700_000_000_000L)

        assertTrue(repositoryOnFile().observeBaselinePresent().first())
    }

    @Test
    fun savedBaselineTimestampSurvivesRepositoryRecreation() = runTest(dispatcher) {
        repositoryOnFile().markBaselineSaved(savedAtMillis = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, repositoryOnFile().observeBaselineSavedAtMillis().first())
    }
}
