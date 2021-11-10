package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.time.LocalDateTime

class OppfolgingstilfelleService {
    fun oppfolgingstilfelleList(
        personIdentNumber: PersonIdentNumber,
    ) = listOf(
        Oppfolgingstilfelle(
            personIdentNumber = personIdentNumber,
            start = LocalDateTime.now().minusDays(1),
            end = LocalDateTime.now().plusDays(1),
            virksomhetsnummerList = emptyList(),
        ),
    )
}
