package no.nav.syfo.oppfolgingstilfelle.api.domain

import java.time.LocalDateTime

data class OppfolgingstilfelleDTO(
    val personIdent: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val virksomhetsnummerList: List<String>,
)
