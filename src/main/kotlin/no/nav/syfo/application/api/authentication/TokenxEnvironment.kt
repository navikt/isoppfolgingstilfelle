package no.nav.syfo.application.api.authentication

data class TokenxEnvironment(
    val clientId: String,
    val endpoint: String,
    val wellKnownUrl: String,
    val privateJWK: String
)
