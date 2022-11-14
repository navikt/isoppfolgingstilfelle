package testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val redisStore = RedisStore(
        redisEnvironment = externalMockEnvironment.environment.redis,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        redisStore = redisStore,
    )
}
