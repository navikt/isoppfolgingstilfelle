package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.OppfolgingstilfelleRepository
import no.nav.syfo.infrastructure.database.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import no.nav.syfo.personhendelse.db.getDodsdato
import no.nav.syfo.util.tomorrow

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleRepository: OppfolgingstilfelleRepository,
) {
    fun getOppfolgingstilfeller(
        personIdent: PersonIdentNumber,
    ): List<Oppfolgingstilfelle> {
        val oppfolgingstilfelleList: List<Oppfolgingstilfelle> =
            oppfolgingstilfellePerson(
                personIdent = personIdent,
            )?.oppfolgingstilfelleList?.filter {
                it.start.isBefore(tomorrow())
            } ?: emptyList()
        return oppfolgingstilfelleList.sortedByDescending { tilfelle -> tilfelle.start }
    }

    fun getDodsdato(personIdent: PersonIdentNumber) = database.connection.use {
        it.getDodsdato(personIdent)
    }

    private fun oppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdent = personIdent)
        val dodsdato = database.connection.use { connection ->
            connection.getDodsdato(personIdent)
        }
        return oppfolgingstilfellePerson?.toOppfolgingstilfellePerson(dodsdato)
    }
}
