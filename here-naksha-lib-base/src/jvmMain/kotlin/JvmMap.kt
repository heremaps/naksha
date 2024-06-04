@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.undefinedCache
import java.util.Map
import kotlin.reflect.KClass

/**
 * The JVM implementation of a [PlatformMap].
 */
open class JvmMap(vararg entries: Any?) : JvmObject(), Map<String, Any?>, PlatformMap {
    init {
        var i = 0
        while (i < entries.size) {
            val keyIndex = i
            val key = entries[i++]
            val value = if (i < entries.size) entries[i++] else null
            when (key) {
                is String -> set(key, value)
                is Symbol -> set(key, value) // TODO: Fix me!
                else -> throw IllegalArgumentException("key at index $keyIndex is no string and no symbol")
            }
        }
    }

    override fun size(): Int = propertiesCount()

    override fun isEmpty(): Boolean = propertiesCount() == 0

    override fun containsKey(key: Any?): Boolean {
        if (key is String) return contains(key)
        if (key is Symbol) return contains(key)
        return false
    }

    override fun containsValue(value: Any?): Boolean {
        val p = properties ?: return false
        for (key in p.keys) if (p[key] == value) return true
        return false
    }

    override fun get(key: Any?): Any? {
        if (key is String) return get(key)
        if (key is Symbol) return get(key)
        return undefinedCache[Any::class]
    }

    override fun put(key: String?, value: Any?): Any? {
        require(key != null)
        return set(key, value)
    }

    override fun remove(key: Any?): Any? {
        if (key is String) return remove(key)
        if (key is Symbol) return remove(key)
        return undefinedCache[Any::class]
    }

    override fun clear() {
        properties = null
    }

    override fun keySet(): MutableSet<String> = properties().keys

    override fun values(): MutableCollection<Any?> = properties().values

    override fun entrySet(): MutableSet<MutableMap.MutableEntry<String, Any?>> = properties().entries

    override fun putAll(m: MutableMap<out String, out Any?>) {
        properties().putAll(m)
    }

    override fun <K : Any, V : Any, T : P_Map<K, V>> proxy(
        klass: KClass<out T>,
        keyKlass: KClass<out K>?,
        valueKlass: KClass<out V>?,
        doNotOverride: Boolean
    ): T {
        TODO("Not yet implemented")
    }
}
