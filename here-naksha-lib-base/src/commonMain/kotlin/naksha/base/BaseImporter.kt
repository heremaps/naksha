package naksha.base

import kotlin.js.JsExport

/**
 * A lambda to import native objects into multi-platform objects.
 * @since 3.0
 */
@JsExport
interface BaseImporter {
    /**
     * Tests if the given native object can be converted into a multi-platform object, using this importer.
     * @param raw The native object.
     * @return _true_ if the given native object can be converted into a multi-platform object; _false_ otherwise.
     * @since 3.0
     */
    fun canImport(raw: Any?): Boolean

    /**
     * A method called to convert the given native object into a multi-platform object. This method is only invoked,
     * when [canImport] returns _true_.
     * @param raw The native object.
     * @return The multi-platform representation of the given native object.
     * @since 3.0
     */
    fun importFromPlatform(raw: Any?): Any?
}
