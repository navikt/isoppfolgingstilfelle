package no.nav.syfo.client.narmesteLeder

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class NarmesteLederClient(
    narmesteLederBaseUrl: String,
    private val narmestelederClientId: String,
    private val tokendingsClient: TokendingsClient,
    private val redisStore: RedisStore,
) {
    private val ansatteNarmesteLederSelvbetjeningPath = "$narmesteLederBaseUrl$NARMESTELEDERE_SELVBETJENING_PATH"

    private val httpClient = httpClientDefault()

    suspend fun getAktiveAnsatte(
        narmesteLederIdent: PersonIdentNumber,
        tokenx: String? = null,
        callId: String,
    ): List<NarmesteLederRelasjonDTO> {
        val cacheKey = "$CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX${narmesteLederIdent.value}"
        val cachedNarmesteLedere = redisStore.getListObject<NarmesteLederRelasjonDTO>(cacheKey)
        return if (cachedNarmesteLedere != null) {
            cachedNarmesteLedere
        } else {
            val token = tokenx?.let {
                tokendingsClient.getOnBehalfOfToken(
                    scopeClientId = narmestelederClientId,
                    token = it,
                ).accessToken
            } ?: throw RuntimeException("Could not get AktiveAnsatte: Failed to get token x")

            val path = ansatteNarmesteLederSelvbetjeningPath
            try {
                val narmesteLedere = httpClient.get(path) {
                    header(HttpHeaders.Authorization, bearerHeader(token))
                    header(NAV_PERSONIDENT_HEADER, narmesteLederIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                }.body<List<NarmesteLederRelasjonDTO>>()
                redisStore.setObject(
                    cacheKey,
                    narmesteLedere,
                    CACHE_NARMESTE_LEDER_EXPIRE_SECONDS,
                )
                narmesteLedere.filter { it.narmesteLederPersonIdentNumber == narmesteLederIdent.value }
            } catch (e: ClientRequestException) {
                handleUnexpectedResponseException(e.response)
                emptyList()
            } catch (e: ServerResponseException) {
                handleUnexpectedResponseException(e.response)
                emptyList()
            }
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
    ) {
        log.error(
            "Error while requesting current NarmesteLeder of person from Narmesteleder with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
        const val CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX = "narmeste-leder-aktive-ansatte-"
        const val CACHE_NARMESTE_LEDER_EXPIRE_SECONDS = 3600L

        const val CURRENT_NARMESTELEDER_PATH = "/api/v1/narmestelederrelasjon/personident"
        const val NARMESTELEDERE_SYSTEM_PATH = "/api/system/v1/narmestelederrelasjoner"
        const val NARMESTELEDERE_SELVBETJENING_PATH = "/api/selvbetjening/v1/narmestelederrelasjoner"
    }
}
