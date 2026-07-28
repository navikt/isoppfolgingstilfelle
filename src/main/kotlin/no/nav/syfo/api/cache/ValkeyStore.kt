package no.nav.syfo.api.cache

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.util.configuredJacksonMapper
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisConnectionException
import kotlin.reflect.KClass

class ValkeyStore(
    private val jedisPool: JedisPool,
) : IValkeyStore {
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    override fun get(
        key: String,
    ): String? {
        try {
            jedisPool.resource.use { jedis ->
                return jedis.get(key)
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from valkey! Continuing without cached value", e)
            return null
        }
    }

    override fun get(
        keyList: List<String>,
    ): List<String> {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.mget(*keyList.toTypedArray()).filterNotNull()
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from valkey! Continuing without cached value", e)
            emptyList()
        }
    }

    override fun <T> setObject(
        key: String,
        value: T,
        expireSeconds: Long,
    ) {
        val valueJson = objectMapper.writeValueAsString(value)
        set(key, valueJson, expireSeconds)
    }

    override fun set(
        key: String,
        value: String,
        expireSeconds: Long,
    ) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.setex(
                    key,
                    expireSeconds,
                    value,
                )
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when storing in valkey! Continue without caching", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ValkeyStore::class.java)
    }
}
