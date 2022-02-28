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

    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
        pdlMock.name to pdlMock.server,
        syfoTilgangskontrollMock.name to syfoTilgangskontrollMock.server
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureOpenIdTokenEndpoint = azureAdMock.url,
        pdlUrl = pdlMock.url,
        syfoTilgangskontrollUrl = syfoTilgangskontrollMock.url
    )

    val redisServer = testRedis(
        port = environment.redisPort,
        secret = environment.redisSecret,
    )

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

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
