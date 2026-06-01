package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.KandidatStatus
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SykmeldtUtenArbeidsgiverKandidatRepository(private val database: DatabaseInterface) {

    fun createIfMissing(kandidat: SykmeldtUtenArbeidsgiverKandidat) {
        database.connection.use { connection ->
            val hasExisting = connection.prepareStatement(QUERY_GET_EXISTING_KANDIDAT).use {
                it.setString(1, kandidat.personident.value)
                it.setObject(2, kandidat.tilfelleStart)
                it.executeQuery().next()
            }
            if (!hasExisting) {
                connection.prepareStatement(QUERY_INSERT_KANDIDAT).use {
                    it.setString(1, kandidat.uuid.toString())
                    it.setTimestamp(2, Timestamp.from(kandidat.createdAt.toInstant()))
                    it.setString(3, kandidat.personident.value)
                    it.setString(4, kandidat.aktorId)
                    it.setString(5, kandidat.referanseId)
                    it.setObject(6, kandidat.tilfelleStart)
                    it.setString(7, kandidat.status.name)
                    it.setTimestamp(8, Timestamp.from(kandidat.nextProcessingAt.toInstant()))
                    it.executeUpdate()
                }
                connection.commit()
            }
        }
    }

    fun getKandidaterForProcessing(): List<SykmeldtUtenArbeidsgiverKandidat> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_KANDIDATER_FOR_PROCESSING).use {
                it.executeQuery().toList { toSykmeldtUtenArbeidsgiverKandidat() }
            }
        }

    fun markerFerdig(uuid: UUID) = updateStatus(uuid, KandidatStatus.FERDIG)

    fun markerOversendt(uuid: UUID) {
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_MARKER_OVERSENDT).use {
                it.setTimestamp(1, Timestamp.from(OffsetDateTime.now().toInstant()))
                it.setString(2, uuid.toString())
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    fun markerUtsatt(uuid: UUID, nextProcessingAt: OffsetDateTime) {
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_UTSETT_KANDIDAT).use {
                it.setTimestamp(1, Timestamp.from(nextProcessingAt.toInstant()))
                it.setString(2, uuid.toString())
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun updateStatus(uuid: UUID, status: KandidatStatus) {
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_UPDATE_STATUS).use {
                it.setString(1, status.name)
                it.setString(2, uuid.toString())
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    companion object {
        private const val QUERY_GET_EXISTING_KANDIDAT =
            """
            SELECT id FROM KANDIDAT_UTEN_ARBEIDSGIVER
            WHERE personident = ?
            AND tilfelle_start = ?
            """

        private const val QUERY_INSERT_KANDIDAT =
            """
            INSERT INTO KANDIDAT_UTEN_ARBEIDSGIVER (
                uuid, created_at, personident, aktor_id, referanse_id, tilfelle_start, status, next_processing_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """

        private const val QUERY_GET_KANDIDATER_FOR_PROCESSING =
            """
            SELECT uuid, created_at, personident, aktor_id, referanse_id, status, tilfelle_start, next_processing_at, oversendt_at
            FROM KANDIDAT_UTEN_ARBEIDSGIVER
            WHERE status IN ('NY', 'UTSATT')
            AND oversendt_at IS NULL
            AND next_processing_at <= NOW()
            ORDER BY next_processing_at ASC
            """

        private const val QUERY_UPDATE_STATUS =
            """
            UPDATE KANDIDAT_UTEN_ARBEIDSGIVER
            SET status = ?
            WHERE uuid = ?
            """

        private const val QUERY_UTSETT_KANDIDAT =
            """
            UPDATE KANDIDAT_UTEN_ARBEIDSGIVER
            SET status = 'UTSATT', next_processing_at = ?
            WHERE uuid = ?
            """

        private const val QUERY_MARKER_OVERSENDT =
            """
            UPDATE KANDIDAT_UTEN_ARBEIDSGIVER
            SET oversendt_at = ?, status = 'FERDIG'
            WHERE uuid = ?
            """
    }
}

private fun ResultSet.toSykmeldtUtenArbeidsgiverKandidat() = SykmeldtUtenArbeidsgiverKandidat(
    uuid = UUID.fromString(getString("uuid")),
    personident = PersonIdentNumber(getString("personident")),
    aktorId = getString("aktor_id"),
    referanseId = getString("referanse_id"),
    createdAt = getTimestamp("created_at").toOffsetDateTimeUTC(),
    status = KandidatStatus.valueOf(getString("status")),
    tilfelleStart = getObject("tilfelle_start", LocalDate::class.java),
    nextProcessingAt = getTimestamp("next_processing_at").toOffsetDateTimeUTC(),
    oversendtAt = getTimestamp("oversendt_at")?.toOffsetDateTimeUTC(),
)
