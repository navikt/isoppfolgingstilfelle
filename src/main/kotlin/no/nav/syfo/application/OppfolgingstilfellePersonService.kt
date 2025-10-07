package no.nav.syfo.application

import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.toOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer

class OppfolgingstilfellePersonService(
    val oppfolgingstilfellePersonRepository: OppfolgingstilfellePersonRepository,
    val oppfolgingstilfellePersonProducer: OppfolgingstilfellePersonProducer,
) {
    fun createOppfolgingstilfellePerson(
        oppfolgingstilfelleBit: OppfolgingstilfelleBit,
        oppfolgingstilfelleBitForPersonList: List<OppfolgingstilfelleBit>,
    ) {
        val oppfolgingstilfellePerson = oppfolgingstilfelleBit.toOppfolgingstilfellePerson(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitForPersonList,
            dodsdato = oppfolgingstilfellePersonRepository.getDodsdato(oppfolgingstilfelleBit.personIdentNumber),
        )
        oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson,
        )

        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }
}
