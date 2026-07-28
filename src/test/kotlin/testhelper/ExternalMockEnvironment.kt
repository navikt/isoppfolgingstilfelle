package testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import testhelper.mock.mockHttpClient
import testhelper.mock.wellKnownInternalAzureAD
import testhelper.mock.wellKnownSelvbetjeningMock

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)

    val valkeyStore = InMemoryValkeyStore()

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()

    val oppfolgingstilfellePersonRepository = OppfolgingstilfellePersonRepository(database = database)
    val tilfellebitRepository = TilfellebitRepository(database = database)
    val oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfellePersonRepository)
    val pdlClient = PdlClient(
        azureAdClient = AzureAdClient(
            azureEnviroment = environment.azure,
            valkeyStore = valkeyStore,
            httpClient = mockHttpClient,
        ),
        clientEnvironment = environment.clients.pdl,
        httpClient = mockHttpClient,
    )
    val kandidatRepository = SykmeldtUtenArbeidsgiverKandidatRepository(database = database)

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment()
        }
    }
}
