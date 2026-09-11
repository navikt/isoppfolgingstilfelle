package no.nav.syfo.infrastructure.client.pensjonpen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory
import java.time.LocalDate

class PensjonPenClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    private val uforetrygdPath = "${clientEnvironment.baseUrl}$UFORETRYGD_PATH"

    suspend fun getUforegrad(
        personIdentNumber: PersonIdentNumber,
        dato: LocalDate = LocalDate.now(),
    ): Uforegrad? =
        try {
            val token = azureAdClient.getSystemToken(clientEnvironment.clientId)
                ?: throw RuntimeException("Failed to getUforegrad: No token was found")

            httpClient.get(uforetrygdPath) {
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header(FNR_HEADER, personIdentNumber.value)
                parameter(DATO_PARAM, dato)
            }.body()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                null
            } else {
                logger.error("Noe gikk galt ved henting av uforetrygd", e)
                throw e
            }
        } catch (e: ServerResponseException) {
            logger.error("Noe gikk galt ved henting av uforetrygd", e)
            throw e
        }

    companion object {
        const val UFORETRYGD_PATH = "/api/uforetrygd/uforegrad"
        const val FNR_HEADER = "fnr"
        const val DATO_PARAM = "dato"
        private val logger = LoggerFactory.getLogger(PensjonPenClient::class.java)
    }
}
