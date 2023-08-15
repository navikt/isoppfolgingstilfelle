package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.database.getOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.database.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.personhendelse.db.getDodsdato
import no.nav.syfo.util.tomorrow

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
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
    ) = database.connection.use { connection ->
        val oppfolgingstilfellePerson = connection.getOppfolgingstilfellePerson(
            personIdent = personIdent,
        )
        val dodsdato = connection.getDodsdato(personIdent)
        oppfolgingstilfellePerson?.toOppfolgingstilfellePerson(dodsdato)
    }
}
