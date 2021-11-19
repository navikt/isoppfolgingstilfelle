package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.SENDT
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.SYKEPENGESOKNAD
import no.nav.syfo.oppfolgingstilfelle.bit.toOppfolgingstilfelleDagList
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.groupOppfolgingstilfelleList
import java.time.LocalDateTime
import java.util.*

class OppfolgingstilfelleService {
    fun oppfolgingstilfelleList(
        personIdentNumber: PersonIdentNumber,
    ): List<Oppfolgingstilfelle> =
        createOppfolgingstilfelleList(
            oppfolgingstilfelleBitList = oppfolgingstilfelleBitList(personIdentNumber),
        )

    fun oppfolgingstilfelleBitList(
        personIdentNumber: PersonIdentNumber,
    ) = listOf(
        OppfolgingstilfelleBit(
            uuid = UUID.randomUUID(),
            personIdentNumber = personIdentNumber,
            virksomhetsnummer = "987654321",
            createdAt = LocalDateTime.now(),
            inntruffet = LocalDateTime.now().minusDays(1),
            fom = LocalDateTime.now().minusDays(1),
            tom = LocalDateTime.now().plusDays(1),
            tagList = listOf(
                SYKEPENGESOKNAD,
                SENDT,
            ),
            ressursId = UUID.randomUUID().toString(),
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
