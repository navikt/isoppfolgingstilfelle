package testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.infrastructure.database.OppfolgingstilfelleRepository
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import testhelper.mock.mockHttpClient
import testhelper.mock.wellKnownInternalAzureAD
import testhelper.mock.wellKnownSelvbetjeningMock

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)

    private val redisConfig = environment.valkeyConfig
    val redisServer = testRedis(
        port = redisConfig.valkeyUri.port,
        secret = redisConfig.valkeyPassword,
    )

    val valkeyStore = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(redisConfig.host, redisConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(redisConfig.ssl)
                .password(redisConfig.valkeyPassword)
                .build()
        )
    )

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()

    val oppfolgingstilfelleRepository = OppfolgingstilfelleRepository(database = database)

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also { it.redisServer.start() }
        }
    }
}
