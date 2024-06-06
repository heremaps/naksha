package com.here.naksha.lib.base

import StringList
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
     * @param klass The proxy class.
     * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
     * @return The proxy instance.
     * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
     */
    fun <T : P_List<*>> proxy(klass: KClass<T>, doNotOverride: Boolean = false): T
}