package no.nav.syfo.infrastructure.client.tokendings

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.httpClientProxy
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TokendingsClient(
    private val tokenxClientId: String,
    private val tokenxEndpoint: String,
    tokenxPrivateJWK: String,
    private val httpClient: HttpClient = httpClientProxy(),
) {
    private val privateKey: RSAKey
    private val jwsSigner: JWSSigner
    private val jwsHeader: JWSHeader

    init {
        privateKey = RSAKey.parse(tokenxPrivateJWK)
        jwsSigner = RSASSASigner(privateKey)
        jwsHeader = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(privateKey.keyID)
            .type(JOSEObjectType.JWT)
            .build()
    }

    suspend fun getOnBehalfOfToken(
        scopeClientId: String,
        token: String,
    ): TokenendingsToken {
        val cacheKey = token + scopeClientId
        val cachedToken = tokenMap.get(cacheKey)
        return if (cachedToken?.isExpired() == false) {
            cachedToken
        } else {
            val tokenendingsToken = getAccessToken(
                Parameters.build {
                    append("client_id", tokenxClientId)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                    append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                    append("client_assertion", getClientAssertion().serialize())
                    append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                    append("subject_token", token)
                    append("audience", scopeClientId.replace('.', ':'))
                }
            )?.toTokenendingsToken()
                ?: throw RuntimeException("Failed to get obo-token from tokenendings")
            tokenendingsToken.also {
                tokenMap.put(cacheKey, it)
            }
        }
    }

    private suspend fun getAccessToken(params: Parameters): TokenendingsTokenDTO? {
        return try {
            val response: HttpResponse = httpClient.post(tokenxEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(params))
            }
            response.body<TokenendingsTokenDTO>()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e)
            null
        }
    }

    private fun handleUnexpectedResponseException(
        responseException: ResponseException,
    ) {
        log.error(
            "Error while requesting token from TokenDings with statusCode=${responseException.response.status.value}",
            responseException
        )
    }

    private fun getClientAssertion(): SignedJWT {
        val fromTime = getNotBeforeTime()
        val jwtClaimSet = JWTClaimsSet.Builder()
            .audience(tokenxEndpoint)
            .subject(tokenxClientId)
            .issuer(tokenxClientId)
            .jwtID(UUID.randomUUID().toString())
            .notBeforeTime(fromTime)
            .expirationTime(getExpirationTime())
            .issueTime(fromTime)
            .build()
        return SignedJWT(jwsHeader, jwtClaimSet).also {
            it.sign(jwsSigner)
        }
    }

    private fun getNotBeforeTime(): Date {
        val now = LocalDateTime.now(Clock.systemUTC())
        return Date.from(now.toInstant(ZoneOffset.UTC))
    }

    private fun getExpirationTime(): Date {
        val exp = LocalDateTime.now(Clock.systemUTC()).plusSeconds(10)
        return Date.from(exp.toInstant(ZoneOffset.UTC))
    }

    companion object {
        private val tokenMap = ConcurrentHashMap<String, TokenendingsToken>()
        private val log = LoggerFactory.getLogger(TokendingsClient::class.java)
    }
}
