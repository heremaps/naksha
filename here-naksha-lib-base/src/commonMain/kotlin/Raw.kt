@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * The base class for all raw types.
 */
@JsExport
abstract class Raw {
    /**
     * The method that converts the raw type into a platform type.
     * @return The platform type that represents this raw type.
     */
    abstract fun toPlatform() : Any?
}