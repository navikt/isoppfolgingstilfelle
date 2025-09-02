package testhelper

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient

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
        oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfelleRepository,
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
        veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = externalMockEnvironment.environment.clients.tilgangskontroll,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )
}
