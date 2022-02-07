package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.database.*
import no.nav.syfo.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.kafka.OppfolgingstilfelleProducer
import java.sql.Connection

class OppfolgingstilfelleService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
    val oppfolgingstilfelleProducer: OppfolgingstilfelleProducer,
) {
    fun oppfolgingstilfellePerson(
        personIdent: PersonIdentNumber,
    ): OppfolgingstilfellePerson? {
        val oppfolgingstilfellePerson = database.getOppfolgingstilfellePerson(
            personIdent = personIdent,
        )
        return oppfolgingstilfellePerson?.toOppfolgingstilfellePerson()
    }

    fun createOppfolgingstilfellePerson(
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
        oppfolgingstilfelleBitList.sortedByDescending { bit -> bit.inntruffet }

        val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
        val oppfolgingstilfellePerson = oppfolgingstilfelleBit.toOppfolgingstilfellePerson(
            oppfolgingstilfelleList = oppfolgingstilfelleList,
        )
        connection.createOppfolgingstilfellePerson(
            commit = false,
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )

        oppfolgingstilfelleProducer.sendOppfolgingstilfelle(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }
}
