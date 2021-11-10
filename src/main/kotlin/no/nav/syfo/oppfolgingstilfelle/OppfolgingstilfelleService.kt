package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.time.LocalDate

class OppfolgingstilfelleService {
    fun oppfolgingstilfelleList() = listOf(
        Oppfolgingstilfelle(
            start = LocalDate.now().minusDays(1),
            end = LocalDate.now().plusDays(1),
            virksomhetsnummerList = emptyList(),
        ),
    )
}
