package testhelper

import redis.embedded.RedisServer

fun testRedis(
    port: Int,
    secret: String,
): RedisServer = RedisServer.builder()
    .port(port)
    .setting("requirepass $secret")
    .build()
