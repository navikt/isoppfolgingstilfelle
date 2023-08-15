package no.nav.syfo.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.personhendelse.db.*
import no.nav.syfo.util.kafkaCallId
import org.slf4j.LoggerFactory
import java.util.UUID

class PersonhendelseService(
    private val database: DatabaseInterface,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    suspend fun handlePersonhendelse(personhendelse: Personhendelse) {
        if (
            personhendelse.doedsfall != null &&
            personhendelse.endringstype == Endringstype.OPPRETTET
        ) {
            handleDoedsfall(personhendelse)
        } else if (
            personhendelse.opplysningstype == "DOEDSFALL_V1" &&
            personhendelse.endringstype == Endringstype.ANNULLERT &&
            personhendelse.tidligereHendelseId != null
        ) {
            handleAnnullering(personhendelse)
        }
    }

    private suspend fun handleDoedsfall(personhendelse: Personhendelse) {
        log.info(
            "Received personhendelse: Hendelseid: ${personhendelse.hendelseId}, " +
                "Endringstype: ${personhendelse.endringstype.name}, " +
                "Oppltype: ${personhendelse.opplysningstype}, " +
                "Dato: ${personhendelse.doedsfall?.doedsdato}"
        )
        personhendelse.personidenter.mapNotNull { personIdent ->
            try {
                if (personIdent.isNullOrEmpty() || personIdent.length == 13) {
                    // ikke lag warning for aktoerid'er
                    null
                } else {
                    PersonIdentNumber(personIdent)
                }
            } catch (ex: IllegalArgumentException) {
                log.warn("Invalid personident for Personhendelse", ex)
                null
            }
        }.forEach { personIdent ->
            if (isKnownPersonIdent(personIdent)) {
                database.connection.use { connection ->
                    if (connection.getDodsdato(personIdent) == null) {
                        connection.createPerson(
                            uuid = UUID.randomUUID(),
                            personIdent = personIdent,
                            dodsdato = personhendelse.doedsfall.doedsdato,
                            hendelseId = UUID.fromString(personhendelse.hendelseId)
                        )
                    }
                    connection.commit()
                }
            }
        }
    }

    private fun handleAnnullering(personhendelse: Personhendelse) {
        log.info(
            "Received personhendelse: Hendelseid: ${personhendelse.hendelseId}, " +
                "Endringstype: ${personhendelse.endringstype.name}, " +
                "Oppltype: ${personhendelse.opplysningstype}, " +
                "Tidligere hendelseId: ${personhendelse.tidligereHendelseId}"
        )
        val annullertHendelseId = UUID.fromString(personhendelse.tidligereHendelseId)
        val rowCount = database.deletePersonWithHendelseId(annullertHendelseId)
        if (rowCount > 0) {
            log.info("Slettet $rowCount rader fra person-tabellen pga ANNULLERING")
        }
    }

    private suspend fun isKnownPersonIdent(personIdent: PersonIdentNumber) =
        oppfolgingstilfelleService.getOppfolgingstilfeller(personIdent).isNotEmpty()

    companion object {
        private val log = LoggerFactory.getLogger(PersonhendelseService::class.java)
    }
}
