package testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.cache.RedisStore
import testhelper.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    private val azureAdMock = AzureAdMock()
    private val pdlMock = PdlMock()
    private val tilgangskontrollMock = TilgangskontrollMock()
    private val narmesteLederMock = NarmesteLederMock()
    private val tokendingsMock = TokendingsMock()
    private val arbeidsforholdMock = ArbeidsforholdMock()

    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
        pdlMock.name to pdlMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server,
        narmesteLederMock.name to narmesteLederMock.server,
        tokendingsMock.name to tokendingsMock.server,
        arbeidsforholdMock.name to arbeidsforholdMock.server,
    )

    val environment = testEnvironment(
        azureOpenIdTokenEndpoint = azureAdMock.url,
        pdlUrl = pdlMock.url,
        istilgangskontrollUrl = tilgangskontrollMock.url,
        narmestelederUrl = narmesteLederMock.url,
        tokendingsUrl = tokendingsMock.url,
        arbeidsforholdUrl = arbeidsforholdMock.url,
    )

    val redisServer = testRedis(
        port = environment.redisConfig.redisUri.port,
        secret = environment.redisConfig.redisPassword,
    )

    lateinit var redisStore: RedisStore

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
    this.externalMocks.forEach { (_, externalMock) -> externalMock.start() }
    this.redisServer.start()
}
