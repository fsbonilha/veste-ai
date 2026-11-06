package com.vesteai.feature.lookbook

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Locale-aware creation-date formatting. [Locale] and [ZoneId] are constructor params rather
 * than read from the environment so callers (and tests) pin them explicitly.
 */
class LookbookDateFormatter(locale: Locale, zoneId: ZoneId) {

    private val formatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(zoneId)

    fun format(epochMillis: Long): String = formatter.format(Instant.ofEpochMilli(epochMillis))
}
