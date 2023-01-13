package no.nav.syfo.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.personhendelse.db.createPerson
import no.nav.syfo.personhendelse.db.getDodsdato
import no.nav.syfo.util.kafkaCallId
import org.slf4j.LoggerFactory
import java.util.UUID

class PersonhendelseService(
    private val database: DatabaseInterface,
    private val oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    suspend fun handlePersonhendelse(personhendelse: Personhendelse) {
        if (personhendelse.doedsfall != null && personhendelse.endringstype == Endringstype.OPPRETTET) {
            log.info("Received personhendelse: Hendelseid: ${personhendelse.hendelseId}, Endringstype: ${personhendelse.endringstype.name}, Oppltype: ${personhendelse.opplysningstype}, Dato: ${personhendelse.doedsfall?.doedsdato}")
            personhendelse.personidenter
                .mapNotNull { personIdent ->
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
                }
                .forEach { personIdent ->
                    if (isKnownPersonIdent(personIdent)) {
                        database.connection.use { connection ->
                            if (connection.getDodsdato(personIdent) == null) {
                                connection.createPerson(
                                    uuid = UUID.fromString(personhendelse.hendelseId),
                                    personIdent = personIdent,
                                    dodsdato = personhendelse.doedsfall.doedsdato,
                                )
                            }
                            connection.commit()
                        }
                    }
                }
        }
    }

    suspend fun isKnownPersonIdent(personIdent: PersonIdentNumber) =
        oppfolgingstilfelleService.getOppfolgingstilfeller(kafkaCallId(), personIdent).isNotEmpty()

    companion object {
        private val log = LoggerFactory.getLogger(PersonhendelseService::class.java)
    }
}
