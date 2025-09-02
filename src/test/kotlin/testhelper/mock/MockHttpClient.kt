package testhelper.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.client.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azure.openidConfigTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.clients.tilgangskontroll.baseUrl}") -> tilgangskontrollResponse(request)
                requestUrl.startsWith("/${environment.clients.pdl.baseUrl}") -> pdlMockResponse(request)
                requestUrl.startsWith("/${environment.clients.narmesteLeder.baseUrl}") -> narmesteLederMockResponse(request)
                requestUrl.startsWith("/${environment.tokenx.endpoint}") -> tokendingsMockResponse()
                requestUrl.startsWith("/${environment.clients.arbeidsforhold.baseUrl}") -> arbeidsforholdMockResponse(request)
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
