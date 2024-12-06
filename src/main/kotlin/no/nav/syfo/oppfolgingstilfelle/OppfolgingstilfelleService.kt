package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.database.getOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.database.getOppfolgingstilfelleMedDodsdatoForPersoner
import no.nav.syfo.oppfolgingstilfelle.person.database.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.personhendelse.db.getDodsdato
import no.nav.syfo.util.tomorrow
import java.time.LocalDate

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
) {
    fun getOppfolgingstilfeller(
        personIdent: PersonIdentNumber,
    ): List<Oppfolgingstilfelle> {
        val oppfolgingstilfellePerson = oppfolgingstilfellePerson(
            personIdent = personIdent,
        )
        return oppfolgingstilfellePerson?.oppfolgingstilfelleList?.sortedByTilfelleStartFutureExcluded() ?: emptyList()
    }

    fun getDodsdato(personIdent: PersonIdentNumber) = database.connection.use {
        it.getDodsdato(personIdent)
    }

    fun getOppfolgingstilfellerForPersoner(personIdents: List<PersonIdentNumber>): Map<PersonIdentNumber, Pair<List<Oppfolgingstilfelle>, LocalDate?>> {
        val oppfolgingstilfellePersonerMedDodsdato = database.connection.use { connection ->
            connection.getOppfolgingstilfelleMedDodsdatoForPersoner(personIdenter = personIdents)
        }
        return oppfolgingstilfellePersonerMedDodsdato.mapValues { (_, pair) ->
            val (pOppfolgingstilfellePerson, dodsdato) = pair
            val oppfolgingstilfelleList = pOppfolgingstilfellePerson.toOppfolgingstilfellePerson(dodsdato = dodsdato).oppfolgingstilfelleList.sortedByTilfelleStartFutureExcluded()
            oppfolgingstilfelleList to dodsdato
        }
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

    private fun List<Oppfolgingstilfelle>.sortedByTilfelleStartFutureExcluded(): List<Oppfolgingstilfelle> =
        this.filter {
            it.start.isBefore(tomorrow())
        }.sortedByDescending { it.start }
}
