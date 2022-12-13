package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.identhendelse.database.updateTilfelleBitFnr
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitForIdent
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
                val tilfelleBitWithOldIdentList = inactiveIdenter.flatMap { personident ->
                    database.getOppfolgingstilfelleBitForIdent(personident)
                }

                if (tilfelleBitWithOldIdentList.isNotEmpty()) {
                    checkThatPdlIsUpdated(activeIdent)
                    val numberOfUpdatedIdenter = database.updateTilfelleBitFnr(activeIdent, tilfelleBitWithOldIdentList)
                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter tilfellebiter based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdentNumber) {
        runBlocking {
            val pdlIdenter = pdlClient.pdlIdenter(nyIdent)?.hentIdenter
            if (nyIdent.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == nyIdent.value && it.historisk }) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
