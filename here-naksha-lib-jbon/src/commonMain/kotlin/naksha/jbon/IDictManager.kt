@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A thread safe dictionary manager.
 * @since 3.0
 */
@JsExport
interface IDictManager : IDictReader {

    /**
     * Store the given dictionary into the manager.
     * @param dict the global dictionary to store.
     * @throws IllegalStateException if a dictionary with the same identifier exists already.
     * @since 3.0
     */
    fun putDictionary(dict: JbDictionary)

    /**
     * Deleted the given dictionary from the manager.
     * @param dict the dictionary to delete.
     * @return _true_ if the dictionary was deleted; _false_ if this dictionary is not stored in the manager.
     * @since 3.0
     */
    fun deleteDictionary(dict: JbDictionary): Boolean

    /**
     * The default dictionary to use for encoding.
     * @return default dictionary to use for encoding; _null_ if none is available.
     * @since 3.0
     */
    @Deprecated(
        message = "Please use getEncodingDictionary",
        replaceWith = ReplaceWith("getEncodingDictionary()"),
        level = DeprecationLevel.WARNING
    )
    fun defaultDict(): String? = getEncodingDictionary(null)?.id
}