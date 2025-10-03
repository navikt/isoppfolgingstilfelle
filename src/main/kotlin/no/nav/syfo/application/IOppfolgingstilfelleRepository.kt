package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.POppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import java.sql.Connection
import java.time.LocalDate
import java.util.*

interface IOppfolgingstilfelleRepository {
    fun createOppfolgingstilfellePerson(
        connection: Connection,
        commit: Boolean,
        oppfolgingstilfellePerson: OppfolgingstilfellePerson,
    )

    fun getOppfolgingstilfellePerson(personIdent: PersonIdentNumber): POppfolgingstilfellePerson?

    fun getDodsdato(personident: PersonIdentNumber): LocalDate?

    fun createPerson(
        uuid: UUID,
        personIdent: PersonIdentNumber,
        dodsdato: LocalDate,
        hendelseId: UUID,
    )

    fun deletePersonWithHendelseId(
        hendelseId: UUID,
    ): Int
}
