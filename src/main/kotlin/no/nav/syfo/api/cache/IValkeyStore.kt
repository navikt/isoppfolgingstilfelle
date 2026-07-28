package no.nav.syfo.api.cache

import no.nav.syfo.util.configuredJacksonMapper
import kotlin.reflect.KClass

interface IValkeyStore {
    fun get(key: String): String?
    fun get(keyList: List<String>): List<String>
    fun set(key: String, value: String, expireSeconds: Long)
    fun <T : Any> getObjectList(classType: KClass<T>, keyList: List<String>): List<T>
    fun <T> setObject(key: String, value: T, expireSeconds: Long)
}

@PublishedApi
internal val cacheMapper = configuredJacksonMapper()

inline fun <reified T> IValkeyStore.getObject(key: String): T? {
    return get(key)?.let { cacheMapper.readValue(it, T::class.java) }
}

inline fun <reified T> IValkeyStore.getListObject(key: String): List<T>? {
    val value = get(key)
    return if (value != null) {
        cacheMapper.readValue(
            value,
            cacheMapper.typeFactory.constructCollectionType(ArrayList::class.java, T::class.java)
        )
    } else null
}
