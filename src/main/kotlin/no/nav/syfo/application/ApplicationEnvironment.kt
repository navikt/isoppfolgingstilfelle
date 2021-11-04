package no.nav.syfo.application

data class Environment(
    val applicationName: String = "isoppfolgingstilfelle",
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null,
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
