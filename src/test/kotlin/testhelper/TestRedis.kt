package testhelper

import no.nav.syfo.application.cache.ApplicationEnvironmentRedis
import redis.embedded.RedisServer

fun testRedis(
    redisEnvironment: ApplicationEnvironmentRedis,
): RedisServer = RedisServer.builder()
    .port(redisEnvironment.port)
    .setting("requirepass ${redisEnvironment.secret}")
    .build()
