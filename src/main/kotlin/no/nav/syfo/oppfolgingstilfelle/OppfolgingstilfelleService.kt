package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.database.getOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.database.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import no.nav.syfo.util.tomorrow
import java.time.LocalDate

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val pdlClient: PdlClient,
) {
    suspend fun getOppfolgingstilfeller(
        callId: String,
        personIdent: PersonIdentNumber,
    ): List<Oppfolgingstilfelle> {
        val personIdentList = pdlClient.identList(
            callId = callId,
            personIdentNumber = personIdent,
        )
        val allOppfolgingstilfelleList = mutableListOf<Oppfolgingstilfelle>()
        personIdentList?.forEach { it ->
            val oppfolgingstilfelleList: List<Oppfolgingstilfelle>? =
                oppfolgingstilfellePerson(
                    personIdent = it,
                )?.oppfolgingstilfelleList?.filter {
                    it.start.isBefore(tomorrow())
                }
            if (!oppfolgingstilfelleList.isNullOrEmpty()) {
                allOppfolgingstilfelleList.addAll(oppfolgingstilfelleList)
            }
        }
        return allOppfolgingstilfelleList.sortedByDescending { tilfelle -> tilfelle.start }
    }

    private fun oppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = database.getOppfolgingstilfellePerson(
            personIdent = personIdent,
        )
        return oppfolgingstilfellePerson?.toOppfolgingstilfellePerson()
    }
}
