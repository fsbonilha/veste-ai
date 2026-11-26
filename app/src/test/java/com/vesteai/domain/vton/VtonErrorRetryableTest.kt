package com.vesteai.domain.vton

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decides whether a poll that failed is worth repeating. Retrying a permanent failure wastes the
 * remaining budget; *not* retrying a transient one discards a job the provider is still rendering
 * (and has already billed for), which is the bug this exists to prevent.
 */
class VtonErrorRetryableTest {

    @Test
    fun transportFailuresAreRetryable() {
        assertTrue(VtonError.NoNetwork.isRetryable)
        assertTrue(VtonError.Timeout.isRetryable)
    }

    @Test
    fun rateLimitingAndProviderBackPressureAreRetryable() {
        assertTrue(VtonError.TooManyConcurrentJobs("slow down").isRetryable)
        assertTrue(VtonError.ProcessingBusy(retryable = true).isRetryable)
    }

    @Test
    fun processingBusyRespectsTheProvidersOwnVerdict() {
        assertFalse(VtonError.ProcessingBusy(retryable = false).isRetryable)
    }

    @Test
    fun serverErrorsAreRetryableOnlyWhenTheStatusSaysSo() {
        assertTrue(VtonError.ServerError(500).isRetryable)
        assertTrue(VtonError.ServerError(502).isRetryable)
        assertTrue(VtonError.ServerError(503).isRetryable)
        assertFalse(VtonError.ServerError(400).isRetryable)
    }

    /** These describe the request itself; repeating it produces the same answer. */
    @Test
    fun permanentFailuresAreNotRetryable() {
        assertFalse(VtonError.AuthenticationFailed.isRetryable)
        assertFalse(VtonError.NotConfigured.isRetryable)
        assertFalse(VtonError.UnsupportedCombination.isRetryable)
        assertFalse(VtonError.InvalidImage.isRetryable)
        assertFalse(VtonError.WrongAspectRatio.isRetryable)
        assertFalse(VtonError.BodyNotDetected.isRetryable)
        assertFalse(VtonError.InputTooSmall("200x300").isRetryable)
        assertFalse(VtonError.JobFailed("nope").isRetryable)
    }
}