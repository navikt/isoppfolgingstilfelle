package no.nav.syfo.client

data class ClientsEnvironment(
    val pdl: ClientEnvironment,
    val syfotilgangskontroll: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
