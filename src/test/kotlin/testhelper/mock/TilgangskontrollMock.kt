package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.veiledertilgang.Tilgang
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS

suspend fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith("tilgang/navident/person") -> {
            val personident = request.headers[NAV_PERSONIDENT_HEADER]
            when (personident) {
                PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value -> {
                    respondOk(Tilgang(erGodkjent = false))
                }

                else -> {
                    respondOk(Tilgang(erGodkjent = true))
                }
            }
        }
        requestUrl.endsWith("tilgang/navident/brukere") -> {
            val personidenter = request.receiveBody<List<String>>()
            respondOk(personidenter.filter { it != PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value })
        }
        else -> error("Unhandled path $requestUrl")
    }
}
