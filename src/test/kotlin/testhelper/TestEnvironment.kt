package testhelper

import no.nav.syfo.application.*

fun testEnvironment(
    azureOpenIdTokenEndpoint: String = "azureTokenEndpoint",
    kafkaBootstrapServers: String,
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
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
