package io.holyarrs.tv.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads the API's timestamps (RFC 3339, nanoseconds included) or plain days. */
fun parseDate(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    runCatching { return OffsetDateTime.parse(s).toInstant() }
    runCatching { return LocalDate.parse(s.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }
    return null
}

fun parseDay(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()

fun LocalDate.fmt(pattern: String): String = format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
