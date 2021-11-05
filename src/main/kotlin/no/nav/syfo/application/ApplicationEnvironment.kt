package no.nav.syfo.application

import io.ktor.application.*

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISOPPFOLGINGSTILFELLE_ISOPPFOLGINGSTILFELLE_DB"

data class Environment(
    val isoppfolgingstilfelleDbHost: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
    val isoppfolgingstilfelleDbPort: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
    val isoppfolgingstilfelleDbName: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
    val isoppfolgingstilfelleDbUsername: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
    val isoppfolgingstilfelleDbPassword: String = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$isoppfolgingstilfelleDbHost:$isoppfolgingstilfelleDbPort/$isoppfolgingstilfelleDbName"
    }
}

fun getEnvVar(
    varName: String,
    defaultValue: String? = null,
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
