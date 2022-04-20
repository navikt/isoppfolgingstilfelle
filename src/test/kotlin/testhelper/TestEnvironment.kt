package testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.cache.ApplicationEnvironmentRedis
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
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
    kafkaSykeketilfellebitProcessingEnabled = true,
    redis = ApplicationEnvironmentRedis(
        host = "localhost",
        port = 6379,
        secret = "password",
    ),
    pdlClientId = "dev-fss.pdl.pdl-api",
    pdlUrl = pdlUrl,
    syfotilgangskontrollClientId = "syfotilgangskontroll-client-id",
    syfotilgangskontrollUrl = syfoTilgangskontrollUrl,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
