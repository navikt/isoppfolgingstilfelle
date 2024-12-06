package testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.cache.RedisStore
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import testhelper.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)

    private val redisConfig = environment.redisConfig
    val redisServer = testRedis(
        port = redisConfig.redisUri.port,
        secret = redisConfig.redisPassword,
    )

    val redisStore = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(redisConfig.host, redisConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(redisConfig.ssl)
                .password(redisConfig.redisPassword)
                .build()
        )
    )

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.redisServer.start()
            }
        }
    }
}
