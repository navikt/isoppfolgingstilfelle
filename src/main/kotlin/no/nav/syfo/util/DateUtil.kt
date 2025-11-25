package no.nav.syfo.util

import java.sql.Timestamp
import java.time.*
import java.time.temporal.ChronoUnit

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = this.atZoneSameInstant(
    ZoneId.of("Europe/Oslo")
).toLocalDateTime()

fun OffsetDateTime.toLocalDateOslo(): LocalDate = this.toLocalDateTimeOslo().toLocalDate()

fun Timestamp.toOffsetDateTimeUTC(): OffsetDateTime = this.toInstant().atOffset(defaultZoneOffset)

fun LocalDate.isBeforeOrEqual(date: LocalDate) = !this.isAfter(date)

fun LocalDate.isAfterOrEqual(date: LocalDate) = !this.isBefore(date)

fun tomorrow() = LocalDate.now().plusDays(1)

fun dagerMellomDatoer(startDato: LocalDate, sluttDato: LocalDate): Int {
    return ChronoUnit.DAYS.between(startDato, sluttDato).toInt() + 1
}
