package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.application.cache.ApplicationEnvironmentRedis
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISOPPFOLGINGSTILFELLE_ISOPPFOLGINGSTILFELLE_DB"

data class Environment(
    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),

    val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
    ),

    val kafka: KafkaEnvironment = KafkaEnvironment(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    ),

    val kafkaSykeketilfellebitProcessingEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_SYKETILFELLEBIT_PROCESSING_ENABLED").toBoolean(),

    val redis: ApplicationEnvironmentRedis = ApplicationEnvironmentRedis(
        host = getEnvVar("REDIS_HOST"),
        port = getEnvVar("REDIS_PORT", "6379").toInt(),
        secret = getEnvVar("REDIS_PASSWORD"),
    ),

    val pdlClientId: String = getEnvVar("PDL_CLIENT_ID"),
    val pdlUrl: String = getEnvVar("PDL_URL"),

    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
)

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
