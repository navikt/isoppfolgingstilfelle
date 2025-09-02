package testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.access.PreAuthorizedClient
import no.nav.syfo.api.authentication.TokenxEnvironment
import no.nav.syfo.api.cache.ValkeyConfig
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.ClientsEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
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
    valkeyConfig = ValkeyConfig(
        valkeyUri = URI("http://localhost:6379"),
        valkeyDB = 0,
        valkeyUsername = "valkeyUser",
        valkeyPassword = "valkeyPassword",
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
