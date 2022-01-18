package testhelper

import io.ktor.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
    )
}
