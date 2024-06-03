@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A map that is thread-safe. In JavaScript, there will be no difference between a normal map and a concurrent map, because
 * there is simply no concurrency.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class P_ConcurrentMap<K : Any, V : Any>(keyKlass: KClass<out K>, valueKlass: KClass<out V>)
    : P_Map<K, V>(keyKlass, valueKlass), MutableMap<K, V> {

    override fun createData(): PlatformConcurrentMap = Platform.newConcurrentMap()
    override fun data(): PlatformConcurrentMap = super.data() as PlatformConcurrentMap
}