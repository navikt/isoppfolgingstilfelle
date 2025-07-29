package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.application.api.authentication.JwtIssuer
import no.nav.syfo.application.api.authentication.JwtIssuerType
import no.nav.syfo.application.api.authentication.installJwtAuthentication
import no.nav.syfo.application.metric.api.registerMetricApi
import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.OppfolgingstilfelleRepository
import no.nav.syfo.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.narmesteleder.api.registerOppfolgingstilfelleNarmesteLederApi
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleArbeidstakerApi
import no.nav.syfo.oppfolgingstilfelle.person.api.registerOppfolgingstilfelleSystemApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    oppfolgingstilfelleRepository: OppfolgingstilfelleRepository,
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
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = database,
        oppfolgingstilfelleRepository = oppfolgingstilfelleRepository,
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
            registerOppfolgingstilfelleArbeidstakerApi(
                oppfolgingstilfelleService = oppfolgingstilfelleService,
            )
        }
    }
}
