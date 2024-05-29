@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A lambda to export multi-platform objects to platform specific ones.
 */
@JsExport
interface PlatformExporter {
    /**
     * Tests if the given multi-platform object can be converted using this exporter into a platform specific type.
     * @return _true_ if the given object can be converted; _false_ otherwise.
     */
    fun canExport(raw: Any?): Boolean

    /**
     * A method called to convert the given multi-platform object into a platform native object. This method is only invoked,
     * when [canExport] returns _true_ before.
     * @param raw The multi-platform object.
     * @return The platform native representation of the given object.
     */
    fun exportToPlatform(raw: Any?): Any?
}