@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all Naksha array types.
 */
@JsExport
abstract class OldBaseArrayKlass<E, out T : OldBaseArray<E>> : OldBaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = true

    override fun getPlatformKlass(): Klass<N_Array> = arrayKlass
}
