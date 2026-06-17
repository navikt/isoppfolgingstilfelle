package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import org.slf4j.LoggerFactory

class AktoridCronjob(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
    override val initialDelayMinutes: Long = 5,
    override val intervalDelayMinutes: Long = 1,
) : Cronjob {
    override suspend fun run() {
        try {
            val aktorids: List<String> = database.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT aktor_id FROM AKTORID WHERE personident IS NULL"
                ).use {
                    it.executeQuery().toList {
                        getString("aktor_id")
                    }
                }
            }

            aktorids.forEach { aktorid ->
                val personident = pdlClient.pdlIdenterForAktorId(aktorid)
                    ?.hentIdenter
                    ?.aktivIdent
                if (personident != null) {
                    database.connection.use { connection ->
                        connection.prepareStatement(
                            "UPDATE AKTORID SET personident = ? WHERE aktor_id = ?"
                        ).use {
                            it.setString(1, personident)
                            it.setString(2, aktorid)
                            it.executeUpdate()
                        }
                        connection.commit()
                    }
                } else {
                    log.warn("Fant ikke personident for aktorid")
                }
            }
        } catch (exc: Exception) {
            log.error("Feil ved behandling av aktorid", exc)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AktoridCronjob::class.java)
    }
}
