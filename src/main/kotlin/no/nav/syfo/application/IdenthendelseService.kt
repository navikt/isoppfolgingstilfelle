package no.nav.syfo.application

import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getIdentCount
import no.nav.syfo.infrastructure.database.updatePersonidentOnTilfelleBit
import no.nav.syfo.infrastructure.database.setNewestProcessedTilfelleBitUnprocessed
import no.nav.syfo.infrastructure.database.updateOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.database.updateKandidatUtenArbeidsgiver
import no.nav.syfo.infrastructure.database.updateTilfelleBit
import no.nav.syfo.infrastructure.kafka.identhendelse.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val numberOfUpdatedIdenter = updateAllTables(activeIdent, inactiveIdenter)
                if (numberOfUpdatedIdenter > 0) {
                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter rows based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    private fun updateAllTables(activeIdent: PersonIdentNumber, inactiveIdenter: List<PersonIdentNumber>): Int {
        var numberOfUpdatedIdenter = 0
        val inactiveIdenterCount = database.getIdentCount(inactiveIdenter)

        if (inactiveIdenterCount > 0) {
            checkThatPdlIsUpdated(activeIdent)
            database.connection.use { connection ->
                numberOfUpdatedIdenter += connection.updateTilfelleBit(activeIdent, inactiveIdenter)
                numberOfUpdatedIdenter += connection.updateOppfolgingstilfellePerson(activeIdent, inactiveIdenter)
                numberOfUpdatedIdenter += connection.updateKandidatUtenArbeidsgiver(activeIdent, inactiveIdenter)
                connection.commit()
            }
        }

        return numberOfUpdatedIdenter
    }

    /**
     * Rydder opp i data lagret på en utdatert ident, også når det allerede finnes tilfellebiter og
     * oppfolgingstilfellePerson på gjeldende ident. Tilfellebitene flyttes uten å reprosesseres hver for seg;
     * i stedet settes nyeste prosesserte tilfellebit for gjeldende ident til uprosessert, slik at
     * OppfolgingstilfelleCronjob beregner et nytt oppfolgingstilfellePerson basert på alle tilfellebitene.
     */
    fun handleOutdatedIdent(ident: PersonIdentNumber): Int {
        if (database.getIdentCount(listOf(ident)) == 0) {
            return 0
        }
        val aktivIdent = runBlocking { pdlClient.pdlIdenter(ident) }?.hentIdenter?.aktivIdent
            ?.let { PersonIdentNumber(it) }
            ?: throw RuntimeException("Fant ingen aktiv ident fra PDL")
        if (aktivIdent == ident) {
            log.info("Ident er allerede aktiv ident i PDL, ingenting å oppdatere")
            return 0
        }
        checkThatPdlIsUpdated(aktivIdent)

        var numberOfUpdatedIdenter = 0
        database.connection.use { connection ->
            numberOfUpdatedIdenter += connection.updatePersonidentOnTilfelleBit(aktivIdent, ident)
            numberOfUpdatedIdenter += connection.updateOppfolgingstilfellePerson(aktivIdent, listOf(ident))
            numberOfUpdatedIdenter += connection.updateKandidatUtenArbeidsgiver(aktivIdent, listOf(ident))
            connection.setNewestProcessedTilfelleBitUnprocessed(aktivIdent)
            connection.commit()
        }
        log.info("Utdatert ident: Updated $numberOfUpdatedIdenter rows")
        return numberOfUpdatedIdenter
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdentNumber) {
        runBlocking {
            val pdlIdenter = pdlClient.pdlIdenter(nyIdent)?.hentIdenter ?: throw RuntimeException("Fant ingen identer fra PDL")
            if (nyIdent.value != pdlIdenter.aktivIdent && pdlIdenter.identhendelseIsNotHistorisk(nyIdent.value)) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
