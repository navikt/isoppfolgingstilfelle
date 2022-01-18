package testhelper

import io.ktor.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(
        database = externalMockEnvironment.database,
    )
    val oppfolgingstilfelleService = OppfolgingstilfelleService(
        database = externalMockEnvironment.database,
        oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
    )
}
