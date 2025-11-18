package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.client.ArbeidsforholdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.launchBackgroundTask

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    tilfellebitRepository: TilfellebitRepository,
    valkeyStore: ValkeyStore,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val azureAdClient = AzureAdClient(
        azureEnviroment = environment.azure,
        valkeyStore = valkeyStore
    )
    val arbeidsforholdClient = ArbeidsforholdClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.arbeidsforhold,
    )
    val sykmeldingNyCronjob = SykmeldingNyCronjob(
        database = database,
        arbeidsforholdClient = arbeidsforholdClient,
    )
    val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
        tilfellebitRepository = tilfellebitRepository,
    )
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(
            cronjob = sykmeldingNyCronjob,
        )
    }
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(
            cronjob = oppfolgingstilfelleCronjob,
        )
    }
}
