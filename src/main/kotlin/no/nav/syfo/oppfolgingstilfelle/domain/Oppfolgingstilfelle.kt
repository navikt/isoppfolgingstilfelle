package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import java.time.LocalDate

data class OppfolgingstilfellePerson(
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
)

data class Oppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun OppfolgingstilfellePerson.toOppfolgingstilfellePersonDTO(
    personIdentNumber: PersonIdentNumber,
) = OppfolgingstilfellePersonDTO(
    oppfolgingstilfelleList = this.oppfolgingstilfelleList.toOppfolgingstilfelleDTOList(),
    personIdent = personIdentNumber.value,
)

private fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList() =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }
