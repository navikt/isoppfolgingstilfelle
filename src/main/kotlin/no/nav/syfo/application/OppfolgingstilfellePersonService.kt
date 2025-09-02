package no.nav.syfo.application

import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.toOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.OppfolgingstilfelleRepository
import no.nav.syfo.infrastructure.database.getDodsdato
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import java.sql.Connection

class OppfolgingstilfellePersonService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleRepository: OppfolgingstilfelleRepository,
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
        oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
            connection = connection,
            commit = false,
            oppfolgingstilfellePerson = oppfolgingstilfellePerson,
        )

        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }
}
