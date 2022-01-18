package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.database.createOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.getOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.database.toOppfolgingstilfelleArbeidstaker
import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfelleArbeidstaker
import java.sql.Connection

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun oppfolgingstilfelleArbeidstaker(
        arbeidstakerPersonIdent: PersonIdentNumber,
    ): OppfolgingstilfelleArbeidstaker? {
        val oppfolgingstilfelleArbeidstaker =
            database.getOppfolgingstilfelleArbeidstaker(arbeidstakerPersonIdent = arbeidstakerPersonIdent)

        return oppfolgingstilfelleArbeidstaker?.toOppfolgingstilfelleArbeidstaker()
    }

    fun createOppfolgingstilfelleArbeidstaker(
        connection: Connection,
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
    ) {
        connection.createOppfolgingstilfelleBit(
            commit = false,
            oppfolgingstilfelleBit = oppfolgingstilfelleBit,
        )

        val oppfolgingstilfelleBitList = oppfolgingstilfelleBitService.oppfolgingstilfelleBitList(
            personIdentNumber = oppfolgingstilfelleBit.personIdentNumber
        ).toMutableList()
        oppfolgingstilfelleBitList.add(oppfolgingstilfelleBit)

        val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
        val oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleBit.toOppfolgingstilfelleArbeidstaker(
            oppfolgingstilfelleList = oppfolgingstilfelleList,
        )
        connection.createOppfolgingstilfelleArbeidstaker(
            commit = false,
            oppfolgingstilfelleArbeidstaker = oppfolgingstilfelleArbeidstaker
        )
    }
}
