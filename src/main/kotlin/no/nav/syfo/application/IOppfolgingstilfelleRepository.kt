package no.nav.syfo.application

import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.POppfolgingstilfellePerson
import java.time.LocalDate
import java.util.*

interface IOppfolgingstilfelleRepository {
    fun createOppfolgingstilfellePerson(oppfolgingstilfellePerson: OppfolgingstilfellePerson)

    fun getOppfolgingstilfellePerson(personIdent: PersonIdentNumber): POppfolgingstilfellePerson?

    fun getDodsdato(personident: PersonIdentNumber): LocalDate?

    fun createPerson(
        uuid: UUID,
        personIdent: PersonIdentNumber,
        dodsdato: LocalDate,
        hendelseId: UUID,
    )

    fun deletePersonWithHendelseId(hendelseId: UUID): Int
}
