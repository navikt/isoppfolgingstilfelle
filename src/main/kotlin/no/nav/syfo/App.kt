package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.launchKafkaTaskSyketilfelleBit
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.application.cronjob.launchCronjobModule
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.oppfolgingstilfelle.person.kafka.kafkaOppfolgingstilfelleProducerConfig
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
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                wellKnownSelvbetjening = wellKnownSelvbetjening,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready, running Java VM ${Runtime.version()}")
        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = applicationDatabase,
            oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(),
            lesSykmeldingNy = environment.lesSykmeldingNy,
        )

        launchKafkaTaskSyketilfelleBit(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaSyketilfellebitService = kafkaSyketilfellebitService,
        )
        launchCronjobModule(
            applicationState = applicationState,
            environment = environment,
            database = applicationDatabase,
            oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
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
