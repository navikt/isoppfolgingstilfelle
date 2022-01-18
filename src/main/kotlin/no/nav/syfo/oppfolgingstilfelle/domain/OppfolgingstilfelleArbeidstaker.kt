package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleArbeidstakerDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class OppfolgingstilfelleArbeidstaker(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

data class Oppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun OppfolgingstilfelleArbeidstaker?.toOppfolgingstilfelleArbeidstakerDTO(
    arbeidstakerPersonIdent: PersonIdentNumber,
) = OppfolgingstilfelleArbeidstakerDTO(
    oppfolgingstilfelleList = this?.oppfolgingstilfelleList?.toOppfolgingstilfelleDTOList() ?: emptyList(),
    personIdent = arbeidstakerPersonIdent.value,
)

private fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList() =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }
