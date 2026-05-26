package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

enum class KandidatStatus {
    NY,
    UTSATT,
    FERDIG,
}

data class SykmeldtUtenArbeidsgiverKandidat(
    val uuid: UUID,
    val personident: PersonIdentNumber,
    val aktorId: String,
    val referanseId: String?,
    val createdAt: OffsetDateTime,
    val status: KandidatStatus,
    val nextProcessingAt: OffsetDateTime,
) {
    companion object {
        fun opprett(
            personident: PersonIdentNumber,
            aktorId: String,
            referanseId: String?,
            tilfelleStart: LocalDate,
        ) =
            SykmeldtUtenArbeidsgiverKandidat(
                uuid = UUID.randomUUID(),
                personident = personident,
                aktorId = aktorId,
                referanseId = referanseId,
                createdAt = nowUTC(),
                status = KandidatStatus.NY,
                nextProcessingAt = calculatePlannedProcessingTime(tilfelleStart),
            )
    }
}

private fun calculatePlannedProcessingTime(tilfelleStart: LocalDate): OffsetDateTime {
    val now = nowUTC()
    val processAt = tilfelleStart.plusDays(28).atStartOfDay(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
    return if (now.isBefore(processAt)) processAt else now
}
