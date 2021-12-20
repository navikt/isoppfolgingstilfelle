package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.database.domain.toOppfolgingstilfelleBitList
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitList

class OppfolgingstilfelleBitService(
    val database: DatabaseInterface,
) {
    fun oppfolgingstilfelleBitList(
        personIdentNumber: PersonIdentNumber,
    ) = database.getOppfolgingstilfelleBitList(
        personIdentNumber = personIdentNumber,
    ).toOppfolgingstilfelleBitList()
}
