package no.nav.syfo.client.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val redisStore: RedisStore,
) {
    private val httpClient = httpClientDefault()

    suspend fun identList(
        callId: String,
        personIdentNumber: PersonIdentNumber,
    ): List<PersonIdentNumber>? {
        val cacheKey = personIdentIdenterCacheKey(
            personIdentNumber = personIdentNumber,
        )
        val cachedValue: PdlPersonidentIdenterCache? = redisStore.getObject(key = cacheKey)
        if (cachedValue != null) {
            COUNT_CALL_PDL_IDENTER_CACHE_HIT.increment()
            return cachedValue.personIdentList.map { cachedPersonIdent ->
                PersonIdentNumber(cachedPersonIdent)
            }
        } else {
            COUNT_CALL_PDL_IDENTER_CACHE_MISS.increment()
            return pdlIdenter(
                callId = callId,
                personIdentNumber = personIdentNumber,
            )?.hentIdenter?.let { identer ->
                redisStore.setObject(
                    key = cacheKey,
                    value = PdlPersonidentIdenterCache(
                        personIdentList = identer.identer.map { it.ident }
                    ),
                    expireSeconds = CACHE_PDL_PERSONIDENT_IDENTER_TIME_TO_LIVE_SECONDS
                )
                identer.toPersonIdentNumberList()
            }
        }
    }

    suspend fun pdlIdenter(
        personIdentNumber: PersonIdentNumber,
        callId: String? = null,
    ): PdlHentIdenter? {
        val token = azureAdClient.getSystemToken(clientEnvironment.clientId)
            ?: throw RuntimeException("Failed to send PdlHentIdenterRequest to PDL: No token was found")

        val query = getPdlQuery(
            queryFilePath = "/pdl/hentIdenter.graphql",
        )

        val request = PdlHentIdenterRequest(
            query = query,
            variables = PdlHentIdenterRequestVariables(
                ident = personIdentNumber.value,
                historikk = true,
                grupper = listOf(
                    IdentType.FOLKEREGISTERIDENT.name,
                ),
            ),
        )

        val response: HttpResponse = httpClient.post(clientEnvironment.baseUrl) {
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
            header(IDENTER_HEADER, IDENTER_HEADER)
            setBody(request)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlIdenterResponse = response.body<PdlIdenterResponse>()
                return if (!pdlIdenterResponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_IDENTER_FAIL.increment()
                    pdlIdenterResponse.errors.forEach {
                        logger.error("Error while requesting IdentList from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
                    pdlIdenterResponse.data
                }
            }
            else -> {
                COUNT_CALL_PDL_IDENTER_FAIL.increment()
                logger.error("Request to get IdentList with url: ${clientEnvironment.baseUrl} failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    private fun personIdentIdenterCacheKey(personIdentNumber: PersonIdentNumber) =
        "$CACHE_PDL_PERSONIDENT_IDENTER_KEY_PREFIX${personIdentNumber.value}"

    private fun getPdlQuery(queryFilePath: String): String {
        return this::class.java.getResource(queryFilePath)!!
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        const val CACHE_PDL_PERSONIDENT_IDENTER_KEY_PREFIX = "pdl-personident-identer-"
        const val CACHE_PDL_PERSONIDENT_IDENTER_TIME_TO_LIVE_SECONDS = 12 * 60 * 60L

        const val IDENTER_HEADER = "identer"

        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}
