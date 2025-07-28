package no.nav.syfo.application.cronjob

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.client.arbeidsforhold.ArbeidsforholdClient
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.SykmeldingNyCronjob

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
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
        database = database,
        oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
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
