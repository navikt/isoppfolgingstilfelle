package testhelper

import no.nav.syfo.application.cache.RedisEnvironment
import redis.embedded.RedisServer

fun testRedis(
    redisEnvironment: RedisEnvironment,
): RedisServer = RedisServer.builder()
    .port(redisEnvironment.port)
    .setting("requirepass ${redisEnvironment.secret}")
    .build()
