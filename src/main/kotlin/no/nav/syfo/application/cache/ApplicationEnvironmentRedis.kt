package no.nav.syfo.application.cache

data class ApplicationEnvironmentRedis(
    val host: String,
    val port: Int,
    val secret: String,
)
