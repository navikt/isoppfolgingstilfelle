package testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.access.PreAuthorizedClient
import no.nav.syfo.application.api.authentication.TokenxEnvironment
import no.nav.syfo.application.cache.RedisEnvironment
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
import no.nav.syfo.util.configuredJacksonMapper
import java.net.ServerSocket

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
    kafkaBootstrapServers: String,
    pdlUrl: String,
    syfoTilgangskontrollUrl: String,
    narmestelederUrl: String,
    tokendingsUrl: String,
    arbeidsforholdUrl: String,
) = Environment(
    azure = AzureEnvironment(
        appClientId = "isoppfolgingstilfelle-client-id",
        appClientSecret = "isoppfolgingstilfelle-secret",
        appPreAuthorizedApps = configuredJacksonMapper().writeValueAsString(testAzureAppPreAuthorizedApps),
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
    ),
    tokenx = TokenxEnvironment(
        clientId = "tokenx-client-id",
        endpoint = tokendingsUrl,
        wellKnownUrl = "tokenx-wellknown",
        privateJWK = getDefaultRSAKey().toJSONString()
    ),
    database = DatabaseEnvironment(
        host = "localhost",
        name = "isoppfolgingstilfelle_dev",
        port = "5432",
        password = "password",
        username = "username",
    ),
    kafka = KafkaEnvironment(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
    ),
    clients = ClientsEnvironment(
        pdl = ClientEnvironment(
            baseUrl = pdlUrl,
            clientId = "dev-fss.pdl.pdl-api",
        ),
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = syfoTilgangskontrollUrl,
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
        ),
        narmesteLeder = ClientEnvironment(
            baseUrl = narmestelederUrl,
            clientId = "narmestelederClientId",
        ),
        arbeidsforhold = ClientEnvironment(
            baseUrl = arbeidsforholdUrl,
            clientId = "aaregClientId",
        ),
    ),
    electorPath = "electorPath",
    lesSykmeldingNy = true,
    redis = RedisEnvironment(
        host = "localhost",
        port = 6379,
        secret = "password",
    ),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}

const val testIsdialogmoteClientId = "isdialogmote-client-id"
const val testIsnarmesteLederClientId = "isnarmesteleder-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedClient(
        name = "dev-gcp:teamsykefravr:isdialogmote",
        clientId = testIsdialogmoteClientId,
    ),
)
