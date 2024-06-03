package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe map, where the keys must not be _null_ and values must not be _undefined_. This map does guarantee the
 * insertion order of the keys, so when iterating above the object, the keys stay in order. This is kind an important if the
 * key order is significant, for example when calculating a hash.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Map")
interface PlatformMap : PlatformObject {
    /**
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
     * @param <T> The type to proxy, must extend [Proxy].
     * @param klass The proxy class.
     * @param keyKlass The key class, can be _null_, if the proxy type has a fixed key type.
     * @param keyKlass The value class, can be _null_, if the proxy type has a fixed value type.
     * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
     * @return The proxy instance.
     * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
     */
    fun <K : Any, V : Any, T : P_Map<K, V>> proxy(
        klass: KClass<out T>,
        keyKlass: KClass<out K>? = null,
        valueKlass: KClass<out V>? = null,
        doNotOverride: Boolean = false
    ): T
}
