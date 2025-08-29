package no.nav.syfo.api.authentication

import no.nav.syfo.infrastructure.client.wellknown.WellKnown

data class JwtIssuer(
    val acceptedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown,
)

enum class JwtIssuerType {
    INTERNAL_AZUREAD,
    SELVBETJENING,
}
