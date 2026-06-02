package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.KandidatStatus
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PSykmeldtUtenArbeidsgiverKandidat(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: String,
    val aktorId: String,
    val referanseId: String?,
    val tilfelleStart: LocalDate,
    val status: String,
    val nextProcessingAt: OffsetDateTime,
)

fun ResultSet.toPSykmeldtUtenArbeidsgiverKandidat() = PSykmeldtUtenArbeidsgiverKandidat(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
    personident = getString("personident"),
    aktorId = getString("aktor_id"),
    referanseId = getString("referanse_id"),
    tilfelleStart = getDate("tilfelle_start").toLocalDate(),
    status = getString("status"),
    nextProcessingAt = getTimestamp("next_processing_at").toOffsetDateTimeUTC(),
)

fun PSykmeldtUtenArbeidsgiverKandidat.toKandidat() = SykmeldtUtenArbeidsgiverKandidat(
    uuid = uuid,
    personident = PersonIdentNumber(personident),
    aktorId = aktorId,
    referanseId = referanseId,
    createdAt = createdAt,
    tilfelleStart = tilfelleStart,
    status = KandidatStatus.valueOf(status),
    nextProcessingAt = nextProcessingAt,
)
