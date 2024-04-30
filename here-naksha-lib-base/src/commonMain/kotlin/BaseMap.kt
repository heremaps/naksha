@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A map that exposes all functions of [BasePairs], so simply a public map
 */
@JsExport
open class BaseMap<E>(vararg args: Any?) : BasePairs<E>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseMapKlass<Any?, BaseMap<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is BaseMap<*>

            override fun newInstance(vararg args: Any?): BaseMap<Any?> = BaseMap(*args)
        }
    }

    override fun getKlass(): BaseKlass<*> = klass

    override fun <T> getOr(key: String, klass: Klass<T>, alternative: T): T = super.getOr(key, klass, alternative)
    override fun <T> getOrNull(key: String, klass: Klass<T>): T? = super.getOrNull(key, klass)
    override fun <T> getOrCreate(key: String, klass: Klass<T>, vararg args: Any?): T = super.getOrCreate(key, klass, *args)

    override operator fun get(key: String): E? = super.get(key)

    override operator fun set(key: String, value: E?): E? = super.set(key, value)
}