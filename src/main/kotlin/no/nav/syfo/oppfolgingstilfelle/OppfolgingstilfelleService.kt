package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.groupOppfolgingstilfelleList

class OppfolgingstilfelleService(
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun oppfolgingstilfelleList(
        personIdentNumber: PersonIdentNumber,
    ): List<Oppfolgingstilfelle> =
        createOppfolgingstilfelleList(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitService.oppfolgingstilfelleBitList(
                personIdentNumber = personIdentNumber,
            ),
        )

    fun createOppfolgingstilfelleList(
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
