package no.nav.syfo.api.endpoints

import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
    val dodsdato: LocalDate?,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val antallSykedager: Int?,
    val varighetUker: Int,
    val virksomhetsnummerList: List<String>,
)
