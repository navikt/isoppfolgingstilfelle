package testhelper

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val azureAdClient = AzureAdClient(
        azureEnviroment = externalMockEnvironment.environment.azure,
        valkeyStore = externalMockEnvironment.valkeyStore,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val tokendingsClient = TokendingsClient(
        tokenxClientId = externalMockEnvironment.environment.tokenx.clientId,
        tokenxEndpoint = externalMockEnvironment.environment.tokenx.endpoint,
        tokenxPrivateJWK = externalMockEnvironment.environment.tokenx.privateJWK,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        oppfolgingstilfelleService = externalMockEnvironment.oppfolgingstilfelleService,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        narmesteLederClient = NarmesteLederClient(
            narmesteLederBaseUrl = externalMockEnvironment.environment.clients.narmesteLeder.baseUrl,
            narmestelederClientId = externalMockEnvironment.environment.clients.narmesteLeder.clientId,
            tokendingsClient = tokendingsClient,
            valkeyStore = externalMockEnvironment.valkeyStore,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
        veilederTilgangskontrollClient = TilgangskontrollClient(
            oboTokenProvider = { scopeClientId, token ->
                azureAdClient.getOnBehalfOfToken(scopeClientId, token)?.accessToken
            },
            clientConfig = ClientConfig(
                baseUrl = externalMockEnvironment.environment.clients.tilgangskontroll.baseUrl,
                clientId = externalMockEnvironment.environment.clients.tilgangskontroll.clientId,
            ),
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )
}
