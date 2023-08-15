package testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val redisStore = RedisStore(
        redisEnvironment = externalMockEnvironment.environment.redis,
    )
    val azureAdClient = AzureAdClient(
        azureEnviroment = externalMockEnvironment.environment.azure,
        redisStore = redisStore,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        azureAdClient = azureAdClient,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        redisStore = redisStore,
    )
}
