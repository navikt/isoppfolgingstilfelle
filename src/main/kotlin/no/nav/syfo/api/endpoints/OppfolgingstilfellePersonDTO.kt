package no.nav.syfo.api.endpoints

import java.time.LocalDate
import java.util.UUID

data class OppfolgingstilfellePersonDTO(
    val uuid: UUID,
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
    val dodsdato: LocalDate?,
    val hasGjentakendeSykefravar: Boolean?
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val antallSykedager: Int?,
    val varighetUker: Int,
    val virksomhetsnummerList: List<String>,
)
