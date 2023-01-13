package no.nav.syfo.oppfolgingstilfelle.person

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.toOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.personhendelse.db.getDodsdato
import java.sql.Connection

class OppfolgingstilfellePersonService(
    val database: DatabaseInterface,
    val oppfolgingstilfellePersonProducer: OppfolgingstilfellePersonProducer,
) {
    fun createOppfolgingstilfellePerson(
        connection: Connection,
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
        oppfolgingstilfelleBitForPersonList: List<OppfolgingstilfelleBit>,
    ) {
        val oppfolgingstilfellePerson = oppfolgingstilfelleBit.toOppfolgingstilfellePerson(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitForPersonList,
            dodsdato = connection.getDodsdato(oppfolgingstilfelleBit.personIdentNumber),
        )
        connection.createOppfolgingstilfellePerson(
            commit = false,
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )

        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }
}
