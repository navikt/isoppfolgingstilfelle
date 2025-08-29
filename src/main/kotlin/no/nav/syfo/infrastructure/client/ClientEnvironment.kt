package no.nav.syfo.infrastructure.client

data class ClientsEnvironment(
    val pdl: ClientEnvironment,
    val tilgangskontroll: ClientEnvironment,
    val narmesteLeder: ClientEnvironment,
    val arbeidsforhold: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
