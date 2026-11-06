package com.vesteai.core.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The save-time check is local only (plan §6.4): PixelAPI charges 32 credits per call, so nothing
 * here may reach a network. These cases are the whole contract.
 */
class CredentialFormatTest {

    @Test
    fun aLiveKeyWithAPrefixIsAccepted() {
        assertNull(validateApiKey("pk_live_abcdef123456", "pk_live_"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertNull(validateApiKey("  pk_live_abcdef123456\n", "pk_live_"))
    }

    @Test
    fun aBlankKeyIsRejectedAsBlank() {
        assertEquals(CredentialFormatError.BLANK, validateApiKey("", "pk_live_"))
        assertEquals(CredentialFormatError.BLANK, validateApiKey("   ", "pk_live_"))
        assertEquals(CredentialFormatError.BLANK, validateApiKey("\n\t", "pk_live_"))
    }

    @Test
    fun aTestModeKeyIsRejectedForItsPrefix() {
        assertEquals(
            CredentialFormatError.MISSING_PREFIX,
            validateApiKey("pk_test_abcdef123456", "pk_live_"),
        )
    }

    @Test
    fun aKeyWithNoPrefixIsRejected() {
        assertEquals(
            CredentialFormatError.MISSING_PREFIX,
            validateApiKey("abcdef123456", "pk_live_"),
        )
    }

    @Test
    fun thePrefixAloneIsAcceptedBecauseOnlyShapeIsCheckedLocally() {
        // A real check would need a 32-credit call; the first Try On is the source of truth.
        assertNull(validateApiKey("pk_live_", "pk_live_"))
    }

    @Test
    fun maskingKeepsOnlyTheLastFourCharacters() {
        assertEquals("****3456", maskApiKey("pk_live_abcdef123456"))
    }

    @Test
    fun maskingAKeyBarelyLongerThanTheSuffixStillLeavesFourCharacters() {
        assertEquals("****ive_", maskApiKey("pk_live_"))
    }

    @Test
    fun maskingTrimsWhitespaceFirst() {
        assertEquals("****3456", maskApiKey("  pk_live_abcdef123456  "))
    }

    @Test
    fun theMaskNeverContainsTheWholeKey() {
        val key = "pk_live_abcdef123456"

        assertEquals(false, maskApiKey(key).contains(key))
    }
}