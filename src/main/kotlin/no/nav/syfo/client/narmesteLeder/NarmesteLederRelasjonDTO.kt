package no.nav.syfo.client.narmesteLeder

import java.time.*

data class NarmesteLederRelasjonDTO(
    val uuid: String,
    val arbeidstakerPersonIdentNumber: String,
    val virksomhetsnavn: String?,
    val virksomhetsnummer: String,
    val narmesteLederPersonIdentNumber: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val narmesteLederNavn: String?,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: LocalDateTime,
    val status: String,
)

enum class NarmesteLederRelasjonStatus {
    INNMELDT_AKTIV,
    DEAKTIVERT,
    DEAKTIVERT_ARBEIDSTAKER,
    DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING,
    DEAKTIVERT_LEDER,
    DEAKTIVERT_ARBEIDSFORHOLD,
    DEAKTIVERT_NY_LEDER,
}
