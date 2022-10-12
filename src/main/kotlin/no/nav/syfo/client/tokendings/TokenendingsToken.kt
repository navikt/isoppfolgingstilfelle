package no.nav.syfo.client.tokendings

import java.io.Serializable
import java.time.LocalDateTime

data class TokenendingsToken(
    val accessToken: String,
    val expires: LocalDateTime,
) : Serializable

fun TokenendingsToken.isExpired() = this.expires < LocalDateTime.now().plusSeconds(60)
