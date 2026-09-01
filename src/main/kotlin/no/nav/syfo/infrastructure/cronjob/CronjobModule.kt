package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.cache.IValkeyStore
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.infrastructure.client.ArbeidsforholdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.SykmeldtUtenArbeidsgiverKandidatRepository
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.kafka.StartOppfolgingProducer
import no.nav.syfo.infrastructure.kafka.kafkaStartOppfolgingProducerConfig
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.producer.KafkaProducer

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    tilfellebitRepository: TilfellebitRepository,
    valkeyStore: IValkeyStore,
    pdlClient: PdlClient,
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
    val kandidatRepository = SykmeldtUtenArbeidsgiverKandidatRepository(database = database)
    val startOppfolgingProducer = StartOppfolgingProducer(
        producer = KafkaProducer(
            kafkaStartOppfolgingProducerConfig(
                kafkaEnvironment = environment.kafka,
            )
        )
    )

    val sykmeldingNyCronjob = SykmeldingNyCronjob(
        database = database,
        arbeidsforholdClient = arbeidsforholdClient,
        initialDelayMinutes = environment.sykmeldingNyCronjobInitialDelayMinutes,
        intervalDelayMinutes = environment.sykmeldingNyCronjobIntervalDelayMinutes,
    )
    val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
        tilfellebitRepository = tilfellebitRepository,
        pdlClient = pdlClient,
        kandidatRepository = kandidatRepository,
        intervalDelayMinutes = environment.oppfolgingstilfelleCronjobIntervalDelayMinutes,
    )
    val modiaAOOversendingCronjob = ModiaAOOversendingCronjob(
        oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfellePersonService.oppfolgingstilfellePersonRepository),
        kandidatRepository = kandidatRepository,
        startOppfolgingProducer = startOppfolgingProducer,
        initialDelayMinutes = environment.modiaAOOversendingCronjobInitialDelayMinutes,
        intervalDelayMinutes = environment.modiaAOOversendingCronjobIntervalDelayMinutes,
        sendEnabled = environment.modiaAOSendEnabled,
    )
    val aktoridCronjob = AktoridCronjob(
        database = database,
        pdlClient = pdlClient,
    )
    listOf(sykmeldingNyCronjob, oppfolgingstilfelleCronjob, modiaAOOversendingCronjob, aktoridCronjob).forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(
                cronjob = it,
            )
        }
    }
}
