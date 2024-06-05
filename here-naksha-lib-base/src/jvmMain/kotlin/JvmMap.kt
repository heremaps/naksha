@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.isAssignableFrom
import java.util.Map
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure

open class JvmMap(vararg entries: Any?) : JvmObject(), Map<String, Any?>, PlatformMap {
    init {
        var i = 0
        while (i < entries.size) {
            val keyIndex = i
            val key = entries[i++]
            val value = if (i < entries.size) entries[i++] else null
            when (key) {
                is String -> set(key, value)
                is Symbol -> set(key, value)
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
        return null
    }

    override fun put(key: String?, value: Any?): Any? {
        require(key != null)
        return set(key, value)
    }

    override fun remove(key: Any?): Any? {
        if (key is String) return delete(key)
        if (key is Symbol) return delete(key)
        return null
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

    override fun <K : Any, V : Any, T : P_Map<K, V?>, C : P_Map<*, *>> proxy(
        klass: KClass<C>,
        keyKlass: KClass<out K>?,
        valueKlass: KClass<out V>?,
        doNotOverride: Boolean
    ): T {
        val symbol = Symbols.symbolOf(klass)
        var proxy = get(symbol)
        if (proxy != null) {
            if (isAssignableFrom(klass, proxy::class)) return proxy as T
            if (doNotOverride) throw IllegalStateException("The symbol $symbol is already bound to incompatible type")
        }
        val constructors = klass.constructors
        for (constructor in constructors) {
            if (constructor.parameters.isEmpty()) {
                proxy = constructor.call()
                break
            }
            if (constructor.parameters.size == 1) {
                val p0Klass = constructor.parameters[0].type.jvmErasure
                if (isAssignableFrom(keyKlass as KClass<*>, p0Klass)) {
                    proxy = constructor.call(keyKlass)
                    break
                }
                if (isAssignableFrom(valueKlass as KClass<*>, p0Klass)) {
                    proxy = constructor.call(valueKlass)
                    break
                }
            }
            if (constructor.parameters.size == 2) {
                val p0Klass = constructor.parameters[0].type.jvmErasure
                val p1Klass = constructor.parameters[1].type.jvmErasure
                if (isAssignableFrom(keyKlass as KClass<*>, p0Klass) && isAssignableFrom(valueKlass as KClass<*>, p1Klass)) {
                    proxy = constructor.call(keyKlass, valueKlass)
                    break
                }
                if (isAssignableFrom(valueKlass as KClass<*>, p0Klass) && isAssignableFrom(keyKlass as KClass<*>, p1Klass)) {
                    proxy = constructor.call(valueKlass, keyKlass)
                    break
                }
            }
        }
        if (proxy == null) throw IllegalArgumentException("Failed to create instance of $klass($keyKlass, $valueKlass)")
        return proxy as T
    }
}
