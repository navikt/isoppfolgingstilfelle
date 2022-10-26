package no.nav.syfo.client.azuread

data class AzureEnvironment(
    val appClientId: String,
    val appClientSecret: String,
    val appPreAuthorizedApps: String,
    val appWellKnownUrl: String,
    val openidConfigTokenEndpoint: String,
)
