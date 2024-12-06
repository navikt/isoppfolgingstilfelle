package no.nav.syfo.client.narmesteLeder

import io.ktor.client.*
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
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val ansatteNarmesteLederSelvbetjeningPath = "$narmesteLederBaseUrl$NARMESTELEDERE_SELVBETJENING_PATH"

    suspend fun getAktiveAnsatte(
        narmesteLederIdent: PersonIdentNumber,
        tokenx: String,
        callId: String,
    ): List<NarmesteLederRelasjonDTO> {
        val cacheKey = "$CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX${narmesteLederIdent.value}"
        val cachedAktiveAnsatte = redisStore.getListObject<NarmesteLederRelasjonDTO>(cacheKey)
        return if (cachedAktiveAnsatte != null) {
            cachedAktiveAnsatte
        } else {
            val token =
                tokendingsClient.getOnBehalfOfToken(
                    scopeClientId = narmestelederClientId,
                    token = tokenx,
                ).accessToken

            val path = ansatteNarmesteLederSelvbetjeningPath
            try {
                val ansatte = httpClient.get(path) {
                    header(HttpHeaders.Authorization, bearerHeader(token))
                    header(NAV_PERSONIDENT_HEADER, narmesteLederIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                }.body<List<NarmesteLederRelasjonDTO>>()
                val aktiveAnsatte = ansatte.filter {
                    it.narmesteLederPersonIdentNumber == narmesteLederIdent.value &&
                        it.status == NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name
                }
                redisStore.setObject(
                    cacheKey,
                    aktiveAnsatte,
                    CACHE_NARMESTE_LEDER_EXPIRE_SECONDS,
                )
                aktiveAnsatte
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
        const val NARMESTELEDERE_SELVBETJENING_PATH = "/api/selvbetjening/v1/narmestelederrelasjoner"
    }
}
