package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import java.sql.Timestamp

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

    companion object {
        private val QUERY_GET_EXISTING_KANDIDAT =
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
    }
}
