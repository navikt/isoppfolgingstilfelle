package testhelper

import no.nav.syfo.application.*
import java.net.ServerSocket

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
    kafkaBootstrapServers: String,
    syfoTilgangskontrollUrl: String,
) = Environment(
    azureAppClientId = "isoppfolgingstilfelle-client-id",
    azureAppClientSecret = "isoppfolgingstilfelle-secret",
    azureAppWellKnownUrl = "wellknown",
    azureOpenidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
    isoppfolgingstilfelleDbHost = "localhost",
    isoppfolgingstilfelleDbPort = "5432",
    isoppfolgingstilfelleDbName = "isoppfolgingstilfelle_dev",
    isoppfolgingstilfelleDbUsername = "username",
    isoppfolgingstilfelleDbPassword = "password",
    kafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
    ),
    kafkaSykeketilfellebitProcessingEnabled = true,
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
