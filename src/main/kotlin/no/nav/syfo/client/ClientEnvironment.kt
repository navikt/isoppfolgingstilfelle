package no.nav.syfo.client

data class ApplicationEnvironmentClients(
    val pdl: ApplicationEnvironmentClient,
    val syfotilgangskontroll: ApplicationEnvironmentClient,
)

data class ApplicationEnvironmentClient(
    val baseUrl: String,
    val clientId: String,
)
