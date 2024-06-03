package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe list, where values may be _null_, but not _undefined_.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Array")
interface PlatformList : PlatformObject {
    /**
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
     * @param <T> The type to proxy, must extend [Proxy].
     * @param klass The proxy class.
     * @param elementKlass The element class, can be _null_, if the proxy type has a fixed element.
     * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
     * @return The proxy instance.
     * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
     */
    fun <V : Any, T : P_List<V>> proxy(klass: KClass<out T>, elementKlass: KClass<out V>? = null, doNotOverride: Boolean = false): T
}