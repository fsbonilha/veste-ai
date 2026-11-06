package com.vesteai.core.error

import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppErrorMapperTest {

    @Test
    fun everyVtonErrorMapsToItsOwnAppError() {
        val expectations = listOf(
            VtonError.NotConfigured to AppError.NotConfigured,
            VtonError.AuthenticationFailed to AppError.AuthenticationFailed,
            VtonError.UnsupportedCombination to AppError.UnsupportedCombination,
            VtonError.NoNetwork to AppError.NoNetwork,
            VtonError.Timeout to AppError.Timeout,
            VtonError.TooManyConcurrentJobs("slow down") to
                AppError.TooManyConcurrentJobs("slow down"),
            VtonError.ProcessingBusy(true) to AppError.ProcessingBusy(true),
            VtonError.InputTooSmall("120x160") to AppError.InputTooSmall("120x160"),
            VtonError.WrongAspectRatio to AppError.WrongAspectRatio,
            VtonError.BodyNotDetected to AppError.BodyNotDetected,
            VtonError.InvalidImage to AppError.InvalidImage,
            VtonError.JobFailed("try a plainer background") to
                AppError.JobFailed("try a plainer background"),
            VtonError.ServerError(503) to AppError.ServerError(503),
        )

        expectations.forEach { (source, expected) -> assertEquals(expected, source.toAppError()) }
    }

    @Test
    fun throwableMappingUnwrapsVtonExceptions() {
        val throwable = VtonException(VtonError.AuthenticationFailed)

        assertEquals(AppError.AuthenticationFailed, throwable.toAppError())
    }

    @Test
    fun nonVtonThrowablesBecomeUnknownRatherThanLeakingTheirText() {
        val throwable = IllegalStateException("SQLite: no such column: secret")

        assertEquals(AppError.Unknown, throwable.toAppError())
    }

    @Test
    fun distinctFailuresNeverCollapseIntoOneAnother() {
        assertNotEquals(AppError.Timeout, AppError.NoNetwork)
        assertNotEquals(AppError.ServerError(500), AppError.ServerError(429))
    }
}
