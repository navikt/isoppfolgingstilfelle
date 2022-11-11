package no.nav.syfo.client

data class ClientsEnvironment(
    val pdl: ClientEnvironment,
    val syfotilgangskontroll: ClientEnvironment,
    val narmesteLeder: ClientEnvironment,
    val arbeidsforhold: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
