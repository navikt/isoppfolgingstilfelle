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

const val DAYS_AFTER_TILFELLE_START = 28L

data class SykmeldtUtenArbeidsgiverKandidat(
    val uuid: UUID,
    val personident: PersonIdentNumber,
    val aktorId: String,
    val referanseId: String?,
    val createdAt: OffsetDateTime,
    val tilfelleStart: LocalDate,
    val status: KandidatStatus,
    val nextProcessingAt: OffsetDateTime,
    val oversendtAt: OffsetDateTime?,
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
                tilfelleStart = tilfelleStart,
                status = KandidatStatus.NY,
                nextProcessingAt = calculatePlannedProcessingTime(tilfelleStart),
                oversendtAt = null,
            )
    }
}

private fun calculatePlannedProcessingTime(tilfelleStart: LocalDate): OffsetDateTime {
    val now = nowUTC()
    val processAt = tilfelleStart.plusDays(DAYS_AFTER_TILFELLE_START).atStartOfDay(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
    return if (now.isBefore(processAt)) processAt else now
}
