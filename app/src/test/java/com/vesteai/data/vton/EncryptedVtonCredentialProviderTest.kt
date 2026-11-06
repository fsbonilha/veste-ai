package com.vesteai.data.vton

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The provider is the only seam an engine uses (plan §6.4), so it must mirror the store exactly:
 * null when absent, the raw key when present, and no cached copy surviving a removal.
 */
class EncryptedVtonCredentialProviderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val harness by lazy { CredentialStoreHarness(tmp.root) }

    @After
    fun tearDown() {
        harness.close()
    }

    private fun provider() = EncryptedVtonCredentialProvider(harness.store)

    @Test
    fun absentKeyReadsAsNull() = runTest {
        assertNull(provider().credentials())
    }

    @Test
    fun aSavedKeyIsReturnedToTheEngine() = runTest {
        harness.store.save(KEY, BASE_URL)

        val credentials = provider().credentials()

        assertEquals(KEY, credentials?.apiKey)
        assertEquals(BASE_URL, credentials?.baseUrl)
    }

    @Test
    fun removingTheKeyTakesEffectOnTheNextCall() = runTest {
        harness.store.save(KEY, BASE_URL)
        val provider = provider()

        harness.store.clear()

        assertNull(provider.credentials())
    }

    @Test
    fun credentialsToStringMasksTheKeySoALogLineCannotLeakIt() = runTest {
        harness.store.save(KEY, BASE_URL)

        val rendered = provider().credentials().toString()

        assertFalse(rendered.contains(KEY))
    }

    private companion object {
        const val KEY = "pk_live_testabcdef7890"
        const val BASE_URL = "https://api.pixelapi.dev"
    }
}