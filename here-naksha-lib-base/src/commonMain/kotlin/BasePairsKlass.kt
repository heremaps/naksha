@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all Naksha map types.
 */
@JsExport
abstract class BasePairsKlass<E, out T : BasePairs<E>> : BaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<PObject> = objectKlass
}