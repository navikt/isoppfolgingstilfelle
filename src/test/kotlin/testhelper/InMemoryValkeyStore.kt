package testhelper

import no.nav.syfo.api.cache.IValkeyStore
import no.nav.syfo.util.configuredJacksonMapper
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class InMemoryValkeyStore : IValkeyStore {
    private val store = ConcurrentHashMap<String, String>()
    private val objectMapper = configuredJacksonMapper()

    override fun get(key: String): String? = store[key]

    override fun get(keyList: List<String>): List<String> = keyList.mapNotNull { store[it] }

    override fun set(key: String, value: String, expireSeconds: Long) {
        store[key] = value
    }

    override fun <T> setObject(key: String, value: T, expireSeconds: Long) {
        set(key, objectMapper.writeValueAsString(value), expireSeconds)
    }

    fun clear() {
        store.clear()
    }
}
