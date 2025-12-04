package no.nav.syfo.application

import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import no.nav.syfo.infrastructure.database.toOppfolgingstilfellePerson
import no.nav.syfo.util.tomorrow

class OppfolgingstilfelleService(
    val oppfolgingstilfellePersonRepository: OppfolgingstilfellePersonRepository,
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

    fun getOppfolgingstilfellePerson(personIdent: PersonIdentNumber): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = oppfolgingstilfellePerson(
            personIdent = personIdent,
        )

        return oppfolgingstilfellePerson?.copy(
            oppfolgingstilfelleList = oppfolgingstilfellePerson.oppfolgingstilfelleList
                .filter { it.start.isBefore(tomorrow()) }
                .sortedByDescending { tilfelle -> tilfelle.start },
        )
    }

    fun getDodsdato(personIdent: PersonIdentNumber) =
        oppfolgingstilfellePersonRepository.getDodsdato(personIdent)

    private fun oppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = oppfolgingstilfellePersonRepository.getOppfolgingstilfellePerson(personIdent = personIdent)
        val dodsdato = getDodsdato(personIdent)
        return oppfolgingstilfellePerson?.toOppfolgingstilfellePerson(dodsdato)
    }
}
