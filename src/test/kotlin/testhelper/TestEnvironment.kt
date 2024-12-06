package testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.access.PreAuthorizedClient
import no.nav.syfo.application.api.authentication.TokenxEnvironment
import no.nav.syfo.application.cache.RedisConfig
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
import no.nav.syfo.util.configuredJacksonMapper
import java.net.URI

fun testEnvironment() = Environment(
    azure = AzureEnvironment(
        appClientId = "isoppfolgingstilfelle-client-id",
        appClientSecret = "isoppfolgingstilfelle-secret",
        appPreAuthorizedApps = configuredJacksonMapper().writeValueAsString(testAzureAppPreAuthorizedApps),
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    tokenx = TokenxEnvironment(
        clientId = "tokenx-client-id",
        endpoint = "tokendingsUrl",
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
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
    ),
    clients = ClientsEnvironment(
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "dev-fss.pdl.pdl-api",
        ),
        tilgangskontroll = ClientEnvironment(
            baseUrl = "istilgangskontrollUrl",
            clientId = "dev-gcp.teamsykefravr.istilgangskontroll",
        ),
        narmesteLeder = ClientEnvironment(
            baseUrl = "narmestelederUrl",
            clientId = "narmestelederClientId",
        ),
        arbeidsforhold = ClientEnvironment(
            baseUrl = "arbeidsforholdUrl",
            clientId = "aaregClientId",
        ),
    ),
    electorPath = "electorPath",
    redisConfig = RedisConfig(
        redisUri = URI("http://localhost:6379"),
        redisDB = 0,
        redisUsername = "redisUser",
        redisPassword = "redisPassword",
        ssl = false,
    ),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)

const val testIsdialogmoteClientId = "isdialogmote-client-id"
const val testIsnarmesteLederClientId = "isnarmesteleder-client-id"
const val testMeroppfolgingBackendClientId = "meroppfolging-backend-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedClient(
        name = "dev-gcp:teamsykefravr:isdialogmote",
        clientId = testIsdialogmoteClientId,
    ),
    PreAuthorizedClient(
        name = "dev-gcp:team-esyfo:meroppfolging-backend",
        clientId = testMeroppfolgingBackendClientId,
    ),
)
