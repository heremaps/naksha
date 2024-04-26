@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * The base class for all Naksha map types.
 */
@JsExport
abstract class NakMapKlass<E, out T : NakMap<E>> : NakKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<PObject> = objectKlass
}