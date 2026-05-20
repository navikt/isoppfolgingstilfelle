package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import java.sql.Timestamp

class SykmeldtUtenArbeidsgiverKandidatRepository(private val database: DatabaseInterface) {

    /**
     * Oppretter kandidaten hvis det ikke allerede finnes en aktiv (NY/UTSATT) kandidat for personen.
     */
    fun createIfMissing(kandidat: SykmeldtUtenArbeidsgiverKandidat) {
        database.connection.use { connection ->
            val hasActive = connection.prepareStatement(QUERY_GET_ACTIVE_KANDIDAT).use {
                it.setString(1, kandidat.personIdentNumber.value)
                it.executeQuery().next()
            }
            if (!hasActive) {
                connection.prepareStatement(QUERY_INSERT_KANDIDAT).use {
                    it.setString(1, kandidat.uuid.toString())
                    it.setTimestamp(2, Timestamp.from(kandidat.createdAt.toInstant()))
                    it.setString(3, kandidat.personIdentNumber.value)
                    it.setString(4, kandidat.aktorId)
                    it.setString(5, kandidat.referanseId)
                    it.setString(6, kandidat.status.name)
                    it.setTimestamp(7, Timestamp.from(kandidat.nextProcessingAt.toInstant()))
                    it.executeUpdate()
                }
                connection.commit()
            }
        }
    }

    companion object {
        private val QUERY_GET_ACTIVE_KANDIDAT =
            """
            SELECT id FROM KANDIDAT_UTEN_ARBEIDSGIVER
            WHERE personident = ?
            AND status IN ('NY', 'UTSATT')
            """

        private const val QUERY_INSERT_KANDIDAT =
            """
            INSERT INTO KANDIDAT_UTEN_ARBEIDSGIVER (
                uuid, created_at, personident, aktor_id, referanse_id, status, next_processing_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """
    }
}
