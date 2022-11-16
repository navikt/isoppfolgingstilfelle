package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.api.registerMetricApi
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.narmesteleder.api.registerOppfolgingstilfelleNarmesteLederApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleSystemApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    wellKnownSelvbetjening: WellKnown,
    redisStore: RedisStore,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
            JwtIssuer(
                acceptedAudienceList = listOf(environment.tokenx.clientId),
                jwtIssuerType = JwtIssuerType.SELVBETJENING,
                wellKnown = wellKnownSelvbetjening,
            ),
        ),
    )
    installStatusPages()

    val azureAdClient = AzureAdClient(
        azureEnviroment = environment.azure,
        redisStore = redisStore,
    )
    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenx.clientId,
        tokenxEndpoint = environment.tokenx.endpoint,
        tokenxPrivateJWK = environment.tokenx.privateJWK,
    )
    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = environment.clients.narmesteLeder.baseUrl,
        narmestelederClientId = environment.clients.narmesteLeder.clientId,
        tokendingsClient = tokendingsClient,
        redisStore = redisStore,
    )
    val narmesteLederAccessService = NarmesteLederAccessService(narmesteLederClient = narmesteLederClient)
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.pdl,
        redisStore = redisStore,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = database,
        pdlClient = pdlClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.syfotilgangskontroll,
    )
    val apiConsumerAccessService = APIConsumerAccessService(
        azureAppPreAuthorizedApps = environment.azure.appPreAuthorizedApps,
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerOppfolgingstilfelleApi(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
            registerOppfolgingstilfelleSystemApi(
                apiConsumerAccessService = apiConsumerAccessService,
                authorizedApplicationNames = environment.systemAPIAuthorizedConsumerApplicationNames,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
            )
        }
        authenticate(JwtIssuerType.SELVBETJENING.name) {
            registerOppfolgingstilfelleNarmesteLederApi(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                narmesteLederAccessService = narmesteLederAccessService
            )
        }
    }
}
