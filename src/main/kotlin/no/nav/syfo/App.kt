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

    val oppfolgingstilfellePersonProducer = OppfolgingstilfellePersonProducer(
        kafkaProducerOppfolgingstilfelle = KafkaProducer(
            kafkaOppfolgingstilfelleProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )

    lateinit var oppfolgingstilfelleBitService: OppfolgingstilfelleBitService

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
            val oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
                database = applicationDatabase,
                oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
            )
            oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(
                database = applicationDatabase,
                oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready")
        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = applicationDatabase,
            oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
        )

        if (environment.kafkaSykeketilfellebitProcessingEnabled) {
            launchKafkaTaskSyketilfelleBit(
                applicationState = applicationState,
                kafkaEnvironment = environment.kafka,
                kafkaSyketilfellebitService = kafkaSyketilfellebitService,
            )
        }
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = false)
}
