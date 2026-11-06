package com.vesteai.data.vton

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VtonCredentialProviderTest {

    @Test
    fun fakeProvider_withoutKey_returnsNull() = runTest {
        val provider = FakeVtonCredentialProvider()

        assertNull(provider.credentials())
    }

    @Test
    fun fakeProvider_withKey_returnsStoredCredentials() = runTest {
        val stored = VtonCredentials(apiKey = "pk_live_test", baseUrl = "https://example.test")
        val provider = FakeVtonCredentialProvider(stored)

        assertEquals(stored, provider.credentials())
    }

    @Test
    fun credentialsToString_doesNotLeakApiKey() {
        val credentials = VtonCredentials(apiKey = "pk_live_secret123", baseUrl = "https://example.test")

        assertFalse(credentials.toString().contains("pk_live_secret123"))
    }
}
