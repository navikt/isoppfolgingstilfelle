package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.database.getOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.database.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val pdlClient: PdlClient,
) {
    suspend fun oppfolgingstilfelleList(
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
                )?.oppfolgingstilfelleList
            if (!oppfolgingstilfelleList.isNullOrEmpty()) {
                allOppfolgingstilfelleList.addAll(oppfolgingstilfelleList)
            }
        }
        return allOppfolgingstilfelleList.sortedByDescending { tilfelle -> tilfelle.start }
    }

    /*
    * Finds start date of oppfolgingstilfelle that overlaps with current date minus 4 months or else
    * returns current date minus 4 months
    * */
    suspend fun oppfolgingstilfelleStartDateOrMinusFourMonthsDate(
        callId: String,
        personIdent: PersonIdentNumber,
    ): LocalDate {
        val oppfolgingstilfelleList = oppfolgingstilfelleList(callId, personIdent)

        val minusFourMonthsDate = LocalDate.now().minusMonths(4)

        val oppfolgingstilfelleStartDateOrMinusFourMonthsDate = oppfolgingstilfelleList
            .find {
                it.start.isBeforeOrEqual(minusFourMonthsDate) && it.end.isAfterOrEqual(minusFourMonthsDate)
            }?.start ?: minusFourMonthsDate

        return oppfolgingstilfelleStartDateOrMinusFourMonthsDate
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
