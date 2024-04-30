@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all Naksha array types.
 */
@JsExport
abstract class BaseArrayKlass<E, out T : BaseArray<E>> : BaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = true

    override fun getPlatformKlass(): Klass<PArray> = arrayKlass
}
