package no.nav.syfo.infrastructure.client.tokendings

import java.time.LocalDateTime

data class TokenendingsTokenDTO(
    val access_token: String,
    val issued_token_type: String,
    val token_type: String,
    val expires_in: Int,
)

fun TokenendingsTokenDTO.toTokenendingsToken() =
    TokenendingsToken(
        accessToken = this.access_token,
        expires = LocalDateTime.now().plusSeconds(this.expires_in.toLong()),
    )
