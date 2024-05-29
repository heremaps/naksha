@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A lambda to import platform specific types into multi-platform format.
 */
@JsExport
interface PlatformImporter {
    /**
     * Tests if the given object can be converted using this importer.
     * @return _true_ if the given object can be converted; _false_ otherwise.
     */
    fun canImport(raw: Any?): Boolean

    /**
     * A method called to convert the given platform native object into a multi-platform object. This method is only invoked,
     * when [canImport] returns _true_ before.
     * @param raw The platform object.
     * @return The multi-platform representation of the given object.
     */
    fun importFromPlatform(raw: Any?): Any?
}
