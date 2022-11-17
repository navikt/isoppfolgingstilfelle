package no.nav.syfo.client.arbeidsforhold

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class ArbeidsforholdClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
) {

    private val arbeidsforholdPath = "${clientEnvironment.baseUrl}$ARBEIDSFORHOLD_PATH"

    private val httpClient = httpClientDefault()

    suspend fun getArbeidsforhold(personIdent: PersonIdentNumber): List<AaregArbeidsforhold> =
        try {
            val token = azureAdClient.getSystemToken(clientEnvironment.clientId)
                ?: throw RuntimeException("Failed to getArbeidsforhold: No token was found")

            httpClient.get(
                "$arbeidsforholdPath?" +
                    "sporingsinformasjon=false&" +
                    "arbeidsforholdstatus=AKTIV,FREMTIDIG,AVSLUTTET"
            ) {
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
            }.body()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                emptyList()
            } else {
                logger.error("Noe gikk galt ved henting av arbeidsforhold", e)
                throw e
            }
        } catch (e: ServerResponseException) {
            logger.error("Noe gikk galt ved henting av arbeidsforhold", e)
            throw e
        }

    companion object {
        const val ARBEIDSFORHOLD_PATH = "/api/v2/arbeidstaker/arbeidsforhold"
        private val logger = LoggerFactory.getLogger(ArbeidsforholdClient::class.java)
    }
}
