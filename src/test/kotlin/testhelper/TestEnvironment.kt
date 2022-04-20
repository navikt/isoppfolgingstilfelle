package testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.cache.ApplicationEnvironmentRedis
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ApplicationEnvironmentClient
import no.nav.syfo.client.ApplicationEnvironmentClients
import no.nav.syfo.client.azuread.AzureEnvironment
import java.net.ServerSocket

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
    kafkaBootstrapServers: String,
    pdlUrl: String,
    syfoTilgangskontrollUrl: String,
) = Environment(
    azure = AzureEnvironment(
        appClientId = "isoppfolgingstilfelle-client-id",
        appClientSecret = "isoppfolgingstilfelle-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
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
    kafkaSyketilfellebitProcessingEnabled = true,
    clients = ApplicationEnvironmentClients(
        pdl = ApplicationEnvironmentClient(
            baseUrl = pdlUrl,
            clientId = "dev-fss.pdl.pdl-api",
        ),
        syfotilgangskontroll = ApplicationEnvironmentClient(
            baseUrl = syfoTilgangskontrollUrl,
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
        ),
    ),
    redis = ApplicationEnvironmentRedis(
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
