package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.KafkaSyketilfellebitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.launchKafkaTaskSyketilfelleBit
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.application.cronjob.launchCronjobModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus.KafkaSykmeldingstatusService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus.launchKafkaTaskStatusendring
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.oppfolgingstilfelle.person.kafka.kafkaOppfolgingstilfelleProducerConfig
import no.nav.syfo.personhendelse.kafka.launchKafkaTaskPersonhendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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
    val valkeyConfig = environment.valkeyConfig
    val valkeyStore = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.valkeyUsername)
                .password(valkeyConfig.valkeyPassword)
                .database(valkeyConfig.valkeyDB)
                .build()
        )
    )
    val azureAdClient = AzureAdClient(
        azureEnviroment = environment.azure,
        valkeyStore = valkeyStore,
    )
    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenx.clientId,
        tokenxEndpoint = environment.tokenx.endpoint,
        tokenxPrivateJWK = environment.tokenx.privateJWK,
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.pdl,
    )

    val oppfolgingstilfellePersonProducer = OppfolgingstilfellePersonProducer(
        kafkaProducerOppfolgingstilfelle = KafkaProducer(
            kafkaOppfolgingstilfelleProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )

    lateinit var oppfolgingstilfellePersonService: OppfolgingstilfellePersonService

    val applicationEngineEnvironment = applicationEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        config = HoconApplicationConfig(ConfigFactory.load())
    }

    val server = embeddedServer(
        Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
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
                narmesteLederClient = NarmesteLederClient(
                    narmesteLederBaseUrl = environment.clients.narmesteLeder.baseUrl,
                    narmestelederClientId = environment.clients.narmesteLeder.clientId,
                    tokendingsClient = tokendingsClient,
                    valkeyStore = valkeyStore,
                ),
                veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
                    azureAdClient = azureAdClient,
                    clientEnvironment = environment.clients.tilgangskontroll,
                ),
            )
            monitor.subscribe(ApplicationStarted) { application ->
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

                val kafkaSykmeldingstatusService = KafkaSykmeldingstatusService(
                    database = applicationDatabase,
                )
                launchKafkaTaskStatusendring(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    kafkaSykmeldingstatusService = kafkaSykmeldingstatusService,
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
                )
                launchCronjobModule(
                    applicationState = applicationState,
                    environment = environment,
                    database = applicationDatabase,
                    oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
                    valkeyStore = valkeyStore,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
