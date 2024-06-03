@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A lambda to export multi-platform objects to native objects.
 */
@JsExport
interface PlatformExporter {
    /**
     * Tests if the given multi-platform object can be converted using this exporter into a native object.
     * @return _true_ if the given multi-platform object can be converted; _false_ otherwise.
     */
    fun canExport(raw: Any?): Boolean

    /**
     * A method called to convert the given multi-platform object into a native object. This method is only invoked,
     * when [canExport] returns _true_.
     * @param raw The multi-platform object.
     * @return The native representation of the given multi-platform object.
     */
    fun exportToPlatform(raw: Any?): Any?
}