package testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import testhelper.mock.AzureAdMock
import testhelper.mock.SyfoTilgangskontrollMock
import testhelper.mock.wellKnownInternalAzureAD

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    private val azureAdMock = AzureAdMock()
    private val syfoTilgangskontrollMock = SyfoTilgangskontrollMock()

    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
        syfoTilgangskontrollMock.name to syfoTilgangskontrollMock.server
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureOpenIdTokenEndpoint = azureAdMock.url,
        syfoTilgangskontrollUrl = syfoTilgangskontrollMock.url
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
}
