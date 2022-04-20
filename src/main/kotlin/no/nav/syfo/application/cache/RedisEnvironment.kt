package no.nav.syfo.application.cache

data class RedisEnvironment(
    val host: String,
    val port: Int,
    val secret: String,
)
