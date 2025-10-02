package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.access.APIConsumerAccessService
import no.nav.syfo.api.authentication.JwtIssuer
import no.nav.syfo.api.authentication.JwtIssuerType
import no.nav.syfo.api.authentication.installJwtAuthentication
import no.nav.syfo.api.endpoints.registerOppfolgingstilfelleNarmesteLederApi
import no.nav.syfo.api.metric.registerMetricApi
import no.nav.syfo.application.NarmesteLederAccessService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleArbeidstakerApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleSystemApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    wellKnownSelvbetjening: WellKnown,
    narmesteLederClient: NarmesteLederClient,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
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

    val narmesteLederAccessService = NarmesteLederAccessService(narmesteLederClient = narmesteLederClient)
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
            registerOppfolgingstilfelleArbeidstakerApi(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
            )
        }
    }
}
