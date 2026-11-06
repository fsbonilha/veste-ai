package com.vesteai.data.vton

import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.credentials.SaveOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs the production [EncryptedCredentialStore] over a JVM [CredentialCipherFactory]; see that class
 * for what this does and does not stand in for.
 */
class EncryptedCredentialStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var harness: CredentialStoreHarness
    private val store get() = harness.store

    @Before
    fun setUp() {
        harness = CredentialStoreHarness(tmp.root)
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun absentWhenNothingWasEverSaved() = runTest {
        assertEquals(CredentialState.Absent, store.state())
        assertNull(store.read())
        assertFalse(harness.credentialFile.exists())
    }

    @Test
    fun savedKeyRoundTripsThroughTheEncryptedFile() = runTest {
        assertEquals(SaveOutcome.Saved, store.save(KEY, BASE_URL))

        val credentials = store.read()
        assertNotNull(credentials)
        assertEquals(KEY, credentials?.apiKey)
        assertEquals(BASE_URL, credentials?.baseUrl)
    }

    @Test
    fun savedKeySurvivesARestart() = runTest {
        store.save(KEY, BASE_URL)

        val restarted = harness.restart()

        assertEquals(KEY, restarted.read()?.apiKey)
        assertEquals(BASE_URL, restarted.read()?.baseUrl)
    }

    @Test
    fun credentialFileIsNotPlaintext() = runTest {
        store.save(KEY, BASE_URL)

        val bytes = String(harness.storedBytes, Charsets.ISO_8859_1)
        assertFalse("file contains the api key", bytes.contains(KEY))
        assertFalse("file contains the base url", bytes.contains(BASE_URL))
    }

    @Test
    fun stateMasksTheKeyAndKeepsTheBaseUrl() = runTest {
        store.save(KEY, BASE_URL)

        assertEquals(
            CredentialState.Present(maskedKey = "****6789", baseUrl = BASE_URL),
            store.state(),
        )
    }

    @Test
    fun stateNeverExposesTheKey() = runTest {
        store.save(KEY, BASE_URL)

        assertFalse(store.state().toString().contains(KEY))
    }

    @Test
    fun clearErasesTheKeyAndFallsBackToAbsent() = runTest {
        store.save(KEY, BASE_URL)

        store.clear()

        assertEquals(CredentialState.Absent, store.state())
        assertNull(store.read())
    }

    @Test
    fun clearSurvivesARestart() = runTest {
        store.save(KEY, BASE_URL)
        store.clear()

        assertNull(harness.restart().read())
    }

    @Test
    fun clearLeavesNoReadableKeyMaterialOnDisk() = runTest {
        store.save(KEY, BASE_URL)

        store.clear()

        assertNull(store.read())
        val bytes = String(harness.storedBytes, Charsets.ISO_8859_1)
        assertFalse("cleared file still contains the api key", bytes.contains(KEY))
    }

    @Test
    fun repeatedReadsAfterAWipeNeverOpenASecondStoreOnTheSameFile() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.corrupt = true
        val restarted = harness.restart()

        // DataStore refuses two live instances on one path, so a wipe must not imply reopening.
        assertNull(restarted.read())
        assertNull(restarted.read())
        assertEquals(CredentialState.Unreadable, restarted.state())
    }

    @Test
    fun aSaveAndReadCycleSurvivesALostRecordWithinOneProcess() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.corrupt = true
        val restarted = harness.restart()
        restarted.read()

        restarted.save(NEW_KEY, BASE_URL)
        restarted.save(NEW_KEY, BASE_URL)

        assertEquals(NEW_KEY, restarted.read()?.apiKey)
    }

    @Test
    fun aReplacedKeysetCannotDecryptTheOldRecordSoEnginesSeeNoKey() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.rotateKeyset()

        val restarted = harness.restart()

        assertNull(restarted.read())
        // Engines see "absent"; Settings learns the key has to be entered again.
        assertEquals(CredentialState.Unreadable, restarted.state())
        // The replacement record is written under the keyset that replaced it, so a save from here
        // is durable rather than surviving only until the next restart.
        assertEquals(SaveOutcome.Saved, restarted.save(NEW_KEY, BASE_URL))
        assertEquals(NEW_KEY, harness.restart().read()?.apiKey)
    }

    @Test
    fun aCorruptKeysetDegradesToNoKeyRatherThanCrashing() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.corrupt = true
        val restarted = harness.restart()

        assertNull(restarted.read())
        assertEquals(CredentialState.Unreadable, restarted.state())
    }

    @Test
    fun aCorruptKeysetIsReplacedSoTheNextSaveIsDurable() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.corrupt = true
        val restarted = harness.restart()
        restarted.read()

        assertEquals(SaveOutcome.Saved, restarted.save(NEW_KEY, BASE_URL))

        // The point of dropping the old record: a restart must still find the new key.
        val again = harness.restart()
        assertEquals(NEW_KEY, again.read()?.apiKey)
    }

    @Test
    fun aKeySavedAfterACorruptKeysetIsVisibleImmediately() = runTest {
        store.save(KEY, BASE_URL)
        harness.cipherFactory.corrupt = true
        val restarted = harness.restart()
        restarted.read()

        restarted.save(NEW_KEY, BASE_URL)

        assertEquals(NEW_KEY, restarted.read()?.apiKey)
    }

    @Test
    fun unavailableCipherReportsAbsentAndNeverCrashes() = runTest {
        harness.cipherFactory.unavailable = true
        val restarted = harness.restart()

        assertEquals(CredentialState.Absent, restarted.state())
        assertNull(restarted.read())
    }

    @Test
    fun unavailableCipherRefusesToPretendTheKeyWasSaved() = runTest {
        harness.cipherFactory.unavailable = true
        val restarted = harness.restart()

        assertEquals(SaveOutcome.StorageUnavailable, restarted.save(KEY, BASE_URL))
        assertNull(restarted.read())
    }

    @Test
    fun anUnavailableCipherRecoversOnceItWorksAgain() = runTest {
        harness.cipherFactory.unavailable = true
        val restarted = harness.restart()
        restarted.save(KEY, BASE_URL)

        harness.cipherFactory.unavailable = false

        assertEquals(SaveOutcome.Saved, restarted.save(KEY, BASE_URL))
        assertEquals(KEY, restarted.read()?.apiKey)
    }

    @Test
    fun saveRejectsABlankKeyWithoutWriting() = runTest {
        assertEquals(
            SaveOutcome.InvalidFormat(CredentialFormatError.BLANK),
            store.save("   ", BASE_URL),
        )
        assertFalse(harness.credentialFile.exists())
    }

    @Test
    fun saveRejectsAKeyWithoutTheLivePrefix() = runTest {
        assertEquals(
            SaveOutcome.InvalidFormat(CredentialFormatError.MISSING_PREFIX),
            store.save("pk_test_123456789", BASE_URL),
        )
        assertFalse(harness.credentialFile.exists())
    }

    @Test
    fun aRejectedSaveLeavesAnExistingKeyIntact() = runTest {
        store.save(KEY, BASE_URL)

        store.save("nope", BASE_URL)

        assertEquals(KEY, store.read()?.apiKey)
    }

    @Test
    fun savingAgainReplacesBothTheKeyAndItsBaseUrl() = runTest {
        store.save(KEY, BASE_URL)

        assertEquals(SaveOutcome.Saved, store.save(NEW_KEY, "https://staging.example.test"))

        val credentials = store.read()
        assertEquals(NEW_KEY, credentials?.apiKey)
        assertEquals("https://staging.example.test", credentials?.baseUrl)
    }

    @Test
    fun observeEmitsTheStoredKeyThenAbsent() = runTest {
        val seen = mutableListOf<CredentialState>()
        val job = backgroundScope.launch { store.observe().collect { seen += it } }
        testScheduler.runCurrent()

        store.save(KEY, BASE_URL)
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()
        job.cancel()

        assertTrue(seen.contains(CredentialState.Present("****6789", BASE_URL)))
        assertEquals(CredentialState.Absent, seen.last())
    }

    private companion object {
        const val KEY = "pk_live_testabcdef6789"
        const val NEW_KEY = "pk_live_testzzzzzz4321"
        const val BASE_URL = "https://api.pixelapi.dev"
    }
}