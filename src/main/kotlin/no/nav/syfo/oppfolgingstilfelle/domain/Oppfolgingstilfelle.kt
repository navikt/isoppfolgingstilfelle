package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.util.toLocalDateOslo
import java.time.OffsetDateTime

data class Oppfolgingstilfelle(
    val personIdentNumber: PersonIdentNumber,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun List<Oppfolgingstilfelle>.toOppfolgingstilfellePersonDTO(
    personIdentNumber: PersonIdentNumber,
) = OppfolgingstilfellePersonDTO(
    oppfolgingstilfelleList = this.toOppfolgingstilfelleDTOList(),
    personIdent = personIdentNumber.value,
)

private fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList() =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            start = oppfolgingstilfelle.start.toLocalDateOslo(),
            end = oppfolgingstilfelle.end.toLocalDateOslo(),
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }
