package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleDTO
import java.time.LocalDateTime

data class Oppfolgingstilfelle(
    val personIdentNumber: PersonIdentNumber,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList(): List<OppfolgingstilfelleDTO> =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            personIdent = oppfolgingstilfelle.personIdentNumber.value,
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }
