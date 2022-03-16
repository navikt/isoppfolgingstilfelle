package no.nav.syfo.util

import java.sql.Timestamp
import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = this.atZoneSameInstant(
    ZoneId.of("Europe/Oslo")
).toLocalDateTime()

fun OffsetDateTime.toLocalDateOslo(): LocalDate = this.toLocalDateTimeOslo().toLocalDate()

fun Timestamp.toOffsetDateTimeUTC(): OffsetDateTime = this.toInstant().atOffset(defaultZoneOffset)
