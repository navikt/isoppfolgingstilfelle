package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.FregStatusSjekkResultat
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
    val oversendtAt: OffsetDateTime?,
    val hasSykepengesoknad: Boolean,
    val isUnder18: Boolean?,
    val isOver67: Boolean?,
    val fregStatusSjekk: String?,
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
    oversendtAt = getTimestamp("oversendt_at")?.toOffsetDateTimeUTC(),
    hasSykepengesoknad = getBoolean("has_sykepengesoknad"),
    isUnder18 = getObject("is_under_18") as Boolean?,
    isOver67 = getObject("is_over_67") as Boolean?,
    fregStatusSjekk = getString("freg_status_sjekk"),
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
    oversendtAt = oversendtAt,
    hasSykepengesoknad = hasSykepengesoknad,
    isUnder18 = isUnder18,
    isOver67 = isOver67,
    fregStatusSjekk = fregStatusSjekk?.let { FregStatusSjekkResultat.valueOf(it) },
)
