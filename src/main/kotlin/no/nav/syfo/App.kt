package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.KafkaSyketilfellebitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.launchKafkaTaskSyketilfelleBit
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.application.cronjob.launchCronjobModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring.KafkaStatusendringService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring.launchKafkaTaskStatusendring
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.oppfolgingstilfelle.person.kafka.kafkaOppfolgingstilfelleProducerConfig
import no.nav.syfo.personhendelse.kafka.launchKafkaTaskPersonhendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()
    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )
    val wellKnownSelvbetjening = getWellKnown(
        wellKnownUrl = environment.tokenx.wellKnownUrl
    )
    val redisStore = RedisStore(
        redisEnvironment = environment.redis,
    )
    val azureAdClient = AzureAdClient(
        azureEnviroment = environment.azure,
        redisStore = redisStore,
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.pdl,
        redisStore = redisStore,
    )

    val oppfolgingstilfellePersonProducer = OppfolgingstilfellePersonProducer(
        kafkaProducerOppfolgingstilfelle = KafkaProducer(
            kafkaOppfolgingstilfelleProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )

    lateinit var oppfolgingstilfellePersonService: OppfolgingstilfellePersonService

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        config = HoconApplicationConfig(
            config = ConfigFactory.load(),
        )

        connector {
            port = applicationPort
        }

        module {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
                database = applicationDatabase,
                oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
            )
            apiModule(
                applicationState = applicationState,
                azureAdClient = azureAdClient,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                wellKnownSelvbetjening = wellKnownSelvbetjening,
                redisStore = redisStore,
                pdlClient = pdlClient,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready, running Java VM ${Runtime.version()}")
        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = applicationDatabase,
            oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(),
        )

        launchKafkaTaskSyketilfelleBit(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaSyketilfellebitService = kafkaSyketilfellebitService,
        )

        val kafkaStatusendringService = KafkaStatusendringService(
            database = applicationDatabase,
        )
        launchKafkaTaskStatusendring(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaStatusendringService = kafkaStatusendringService,
        )

        val identhendelseService = IdenthendelseService(
            database = applicationDatabase,
            pdlClient = pdlClient,
        )
        val kafkaIdenthendelseConsumerService = IdenthendelseConsumerService(
            identhendelseService = identhendelseService,
        )
        launchKafkaTaskIdenthendelse(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaIdenthendelseConsumerService = kafkaIdenthendelseConsumerService,
        )

        launchKafkaTaskPersonhendelse(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            database = applicationDatabase,
            pdlClient = pdlClient,
        )
        launchCronjobModule(
            applicationState = applicationState,
            environment = environment,
            database = applicationDatabase,
            oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
            redisStore = redisStore,
        )
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
