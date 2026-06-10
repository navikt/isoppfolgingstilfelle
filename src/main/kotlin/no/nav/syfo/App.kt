package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.core.instrument.Metrics
import no.nav.syfo.api.apiModule
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.api.metric.METRICS_REGISTRY
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import no.nav.syfo.infrastructure.client.wellknown.getWellKnown
import no.nav.syfo.infrastructure.cronjob.launchCronjobModule
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.bit.TilfellebitRepository
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseConsumerService
import no.nav.syfo.infrastructure.kafka.identhendelse.launchKafkaTaskIdenthendelse
import no.nav.syfo.infrastructure.kafka.kafkaOppfolgingstilfelleProducerConfig
import no.nav.syfo.infrastructure.kafka.personhendelse.launchKafkaTaskPersonhendelse
import no.nav.syfo.infrastructure.kafka.syketilfelle.SyketilfellebitConsumer
import no.nav.syfo.infrastructure.kafka.syketilfelle.launchKafkaTaskSyketilfelleBit
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.SykmeldingstatusConsumer
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.launchKafkaTaskStatusendring
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()

    Metrics.addRegistry(METRICS_REGISTRY)
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
        producer = KafkaProducer(
            kafkaOppfolgingstilfelleProducerConfig(
                kafkaEnvironment = environment.kafka
            )
        )
    )

    lateinit var oppfolgingstilfelleService: OppfolgingstilfelleService
    lateinit var oppfolgingstilfellePersonService: OppfolgingstilfellePersonService
    lateinit var oppfolgingstilfellePersonRepository: OppfolgingstilfellePersonRepository
    lateinit var tilfellebitRepository: TilfellebitRepository

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
            oppfolgingstilfellePersonRepository = OppfolgingstilfellePersonRepository(database = applicationDatabase)
            tilfellebitRepository = TilfellebitRepository(database = applicationDatabase)
            oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
                oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
                oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
            )
            oppfolgingstilfelleService = OppfolgingstilfelleService(
                oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                oppfolgingstilfelleService = oppfolgingstilfelleService,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                wellKnownSelvbetjening = wellKnownSelvbetjening,
                narmesteLederClient = NarmesteLederClient(
                    narmesteLederBaseUrl = environment.clients.narmesteLeder.baseUrl,
                    narmestelederClientId = environment.clients.narmesteLeder.clientId,
                    tokendingsClient = tokendingsClient,
                    valkeyStore = valkeyStore,
                ),
                veilederTilgangskontrollClient = TilgangskontrollClient(
                    oboTokenProvider = { scopeClientId, token ->
                        azureAdClient.getOnBehalfOfToken(scopeClientId, token)?.accessToken
                    },
                    clientConfig = ClientConfig(
                        baseUrl = environment.clients.tilgangskontroll.baseUrl,
                        clientId = environment.clients.tilgangskontroll.clientId,
                    ),
                ),
            )
            monitor.subscribe(ApplicationStarted) { application ->
                applicationState.ready = true
                application.environment.log.info("Application is ready, running Java VM ${Runtime.version()}")
                val syketilfellebitConsumer = SyketilfellebitConsumer(
                    oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(tilfellebitRepository),
                )

                launchKafkaTaskSyketilfelleBit(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    syketilfellebitConsumer = syketilfellebitConsumer,
                )

                val sykmeldingstatusConsumer = SykmeldingstatusConsumer(
                    tilfellebitRepository = tilfellebitRepository,
                )
                launchKafkaTaskStatusendring(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    sykmeldingstatusConsumer = sykmeldingstatusConsumer,
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
                    oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
                )
                launchCronjobModule(
                    applicationState = applicationState,
                    environment = environment,
                    database = applicationDatabase,
                    oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
                    tilfellebitRepository = tilfellebitRepository,
                    valkeyStore = valkeyStore,
                    pdlClient = pdlClient,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applicationState.ready = false
        }
    )

    server.start(wait = true)
}
