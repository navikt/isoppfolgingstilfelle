package no.nav.syfo.api.authentication

data class TokenxEnvironment(
    val clientId: String,
    val endpoint: String,
    val wellKnownUrl: String,
    val privateJWK: String,
)
