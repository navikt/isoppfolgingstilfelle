package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.application.api.authentication.TokenxEnvironment
import no.nav.syfo.application.cache.RedisEnvironment
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISOPPFOLGINGSTILFELLE_ISOPPFOLGINGSTILFELLE_DB"

data class Environment(
    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        appPreAuthorizedApps = getEnvVar("AZURE_APP_PRE_AUTHORIZED_APPS"),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),

    val tokenx: TokenxEnvironment = TokenxEnvironment(
        clientId = getEnvVar("TOKEN_X_CLIENT_ID"),
        endpoint = getEnvVar("TOKEN_X_TOKEN_ENDPOINT"),
        wellKnownUrl = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
        privateJWK = getEnvVar("TOKEN_X_PRIVATE_JWK"),
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
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    ),

    val redis: RedisEnvironment = RedisEnvironment(
        host = getEnvVar("REDIS_HOST"),
        port = getEnvVar("REDIS_PORT", "6379").toInt(),
        secret = getEnvVar("REDIS_PASSWORD"),
    ),

    val clients: ClientsEnvironment = ClientsEnvironment(
        pdl = ClientEnvironment(
            baseUrl = getEnvVar("PDL_URL"),
            clientId = getEnvVar("PDL_CLIENT_ID"),
        ),
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
            clientId = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
        ),
        narmesteLeder = ClientEnvironment(
            baseUrl = getEnvVar("NARMESTELEDER_URL"),
            clientId = getEnvVar("NARMESTELEDER_CLIENT_ID")
        ),
        arbeidsforhold = ClientEnvironment(
            baseUrl = getEnvVar("ARBEIDSFORHOLD_URL"),
            clientId = getEnvVar("ARBEIDSFORHOLD_CLIENT_ID"),
        ),
    ),

    val kafkaIdenthendelseUpdatesEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_IDENTHENDELSE_ENABLED").toBoolean(),

    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    private val isdialogmoteApplicationName: String = "isdialogmote",
    val systemAPIAuthorizedConsumerApplicationNames: List<String> = listOf(
        isdialogmoteApplicationName,
    ),
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
