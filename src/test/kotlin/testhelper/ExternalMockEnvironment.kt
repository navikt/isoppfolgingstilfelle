package testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import testhelper.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    private val azureAdMock = AzureAdMock()
    private val pdlMock = PdlMock()
    private val syfoTilgangskontrollMock = SyfoTilgangskontrollMock()
    private val narmesteLederMock = NarmesteLederMock()
    private val tokendingsMock = TokendingsMock()

    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
        pdlMock.name to pdlMock.server,
        syfoTilgangskontrollMock.name to syfoTilgangskontrollMock.server,
        narmesteLederMock.name to narmesteLederMock.server,
        tokendingsMock.name to tokendingsMock.server
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureOpenIdTokenEndpoint = azureAdMock.url,
        pdlUrl = pdlMock.url,
        syfoTilgangskontrollUrl = syfoTilgangskontrollMock.url,
        narmestelederUrl = narmesteLederMock.url,
        tokendingsUrl = tokendingsMock.url
    )

    val redisServer = testRedis(
        redisEnvironment = environment.redis,
    )

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.embeddedEnvironment.start()
    this.externalMocks.forEach { (_, externalMock) -> externalMock.start() }
    this.redisServer.start()
}
