package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.SENDT
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.SYKEPENGESOKNAD
import no.nav.syfo.oppfolgingstilfelle.bit.toOppfolgingstilfelleDagList
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.groupOppfolgingstilfelleList
import java.time.OffsetDateTime
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
            createdAt = OffsetDateTime.now(),
            inntruffet = OffsetDateTime.now().minusDays(1),
            fom = OffsetDateTime.now().minusDays(1),
            tom = OffsetDateTime.now().plusDays(1),
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
