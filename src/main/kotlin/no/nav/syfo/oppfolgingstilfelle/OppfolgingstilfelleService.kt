package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.domain.*

class OppfolgingstilfelleService(
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun oppfolgingstilfellePerson(
        personIdentNumber: PersonIdentNumber,
    ): OppfolgingstilfellePerson {
        val oppfolgingstilfelleList = createoppfolgingstilfelleList(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitService.oppfolgingstilfelleBitList(
                personIdentNumber = personIdentNumber,
            ),
        )
        return OppfolgingstilfellePerson(
            oppfolgingstilfelleList = oppfolgingstilfelleList,
            personIdentNumber = personIdentNumber
        )
    }

    fun createoppfolgingstilfelleList(
        oppfolgingstilfelleBitList: List<OppfolgingstilfelleBit>,
    ): List<Oppfolgingstilfelle> {
        return if (oppfolgingstilfelleBitList.isEmpty()) {
            emptyList()
        } else {
            oppfolgingstilfelleBitList
                .toOppfolgingstilfelleDagList()
                .groupOppfolgingstilfelleList()
        }
    }
}
