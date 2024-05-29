@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all Naksha map types.
 */
@JsExport
abstract class OldBasePairsKlass<E, out T : OldBasePairs<E>> : OldBaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<N_Object> = objectKlass
}