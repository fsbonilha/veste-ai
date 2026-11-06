package com.vesteai.feature.lookbook

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LookbookDateFormatterTest {

    // 2024-03-09T14:06:07Z
    private val millis = 1_710_000_367_000L

    @Test
    fun formatsSameInstantPerLocale() {
        assertEquals(
            "Mar 9, 2024",
            LookbookDateFormatter(Locale.US, ZoneId.of("UTC")).format(millis),
        )
        assertEquals(
            "9 mars 2024",
            LookbookDateFormatter(Locale.FRANCE, ZoneId.of("UTC")).format(millis),
        )
        assertEquals(
            "09.03.2024",
            LookbookDateFormatter(Locale.GERMANY, ZoneId.of("UTC")).format(millis),
        )
    }

    @Test
    fun formatsTheDaysOfTheGivenZoneNotUtc() {
        val lookbook = LookbookDateFormatter(Locale.US, ZoneId.of("America/Los_Angeles"))

        assertEquals("Mar 9, 2024", lookbook.format(millis))
        assertEquals("Mar 8, 2024", lookbook.format(millis - 24 * 60 * 60 * 1000L))
    }

    @Test
    fun isStableAcrossRepeatedCalls() {
        val lookbook = LookbookDateFormatter(Locale.US, ZoneId.of("UTC"))

        assertEquals(lookbook.format(millis), lookbook.format(millis))
    }
}
