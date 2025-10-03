package no.nav.syfo.application

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import org.slf4j.LoggerFactory
import java.util.*

class PersonhendelseService(
    private val database: DatabaseInterface,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
    private val oppfolgingstilfellePersonRepository: OppfolgingstilfellePersonRepository,
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
                if (oppfolgingstilfelleService.getDodsdato(personIdent) == null) {
                    oppfolgingstilfellePersonRepository.createPerson(
                        uuid = UUID.randomUUID(),
                        personIdent = personIdent,
                        dodsdato = personhendelse.doedsfall.doedsdato,
                        hendelseId = UUID.fromString(personhendelse.hendelseId)
                    )
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
        val rowCount = oppfolgingstilfellePersonRepository.deletePersonWithHendelseId(annullertHendelseId)
        if (rowCount > 0) {
            log.info("Slettet $rowCount rader fra person-tabellen pga ANNULLERING")
        }
    }

    private suspend fun isKnownPersonIdent(personIdent: PersonIdentNumber): Boolean =
        oppfolgingstilfelleService.getOppfolgingstilfeller(personIdent).isNotEmpty()

    companion object {
        private val log = LoggerFactory.getLogger(PersonhendelseService::class.java)
    }
}
