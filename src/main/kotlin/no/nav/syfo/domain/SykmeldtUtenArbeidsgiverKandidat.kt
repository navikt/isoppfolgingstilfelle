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
    val personIdentNumber: PersonIdentNumber,
    val aktorId: String,
    val referanseId: String?,
    val createdAt: OffsetDateTime,
    val status: KandidatStatus,
    val nextProcessingAt: OffsetDateTime,
) {
    companion object {
        fun opprett(
            personIdentNumber: PersonIdentNumber,
            aktorId: String,
            referanseId: String?,
            tilfelleStart: LocalDate,
        ): SykmeldtUtenArbeidsgiverKandidat {
            val now = nowUTC()
            val processAt28 = tilfelleStart.plusDays(28).atStartOfDay(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
            return SykmeldtUtenArbeidsgiverKandidat(
                uuid = UUID.randomUUID(),
                personIdentNumber = personIdentNumber,
                aktorId = aktorId,
                referanseId = referanseId,
                createdAt = now,
                status = KandidatStatus.NY,
                nextProcessingAt = if (now.isBefore(processAt28)) processAt28 else now,
            )
        }
    }
}
