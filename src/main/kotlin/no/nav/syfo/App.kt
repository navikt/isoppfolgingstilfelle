package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.KafkaSyketilfellebitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.launchKafkaTaskSyketilfelleBit
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()
    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azureAppWellKnownUrl,
    )

    lateinit var oppfolgingstilfelleService: OppfolgingstilfelleService

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
                environment = environment,
            )
            val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(
                database = applicationDatabase,
            )
            oppfolgingstilfelleService = OppfolgingstilfelleService(
                database = applicationDatabase,
                oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready")
        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = applicationDatabase,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
        )

        if (environment.kafkaSykeketilfellebitProcessingEnabled) {
            launchKafkaTaskSyketilfelleBit(
                applicationState = applicationState,
                applicationEnvironmentKafka = environment.kafka,
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
