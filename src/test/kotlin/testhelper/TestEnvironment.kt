package testhelper

import no.nav.syfo.application.*

fun testEnvironment(
    azureOpenIdTokenEndpoint: String = "azureTokenEndpoint",
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
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
