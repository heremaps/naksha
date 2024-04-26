@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * The base class for all Naksha array types.
 */
@JsExport
abstract class NakArrayKlass<E, out T : NakArray<E>> : NakKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = true

    override fun getPlatformKlass(): Klass<PArray> = arrayKlass
}
