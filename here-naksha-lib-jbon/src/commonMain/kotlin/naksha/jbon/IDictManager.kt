@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A thread safe dictionary manager.
 */
@JsExport
interface IDictManager {

    /**
     * Store the given dictionary into the manager.
     * @param dict The global dictionary to store.
     * @throws IllegalStateException If a dictionary with the same identifier exists already.
     */
    fun putDictionary(dict: JbDictionary)

    /**
     * Deleted the given dictionary from the manager.
     * @param dict The dictionary to delete.
     * @return _true_ if the dictionary was deleted; _false_ if this dictionary is not stored in the manager.
     */
    fun deleteDictionary(dict: JbDictionary): Boolean

    /**
     * Retrieve the dictionary with the given identifier.
     * @param id The dictionary identifier to lookup.
     * @return The global dictionary with the given identifier; _null_ when no such dictionary exists.
     */
    fun getDictionary(id: String): JbDictionary?

    /**
     * The best dictionary to encode the given feature.
     * @param feature the feature to encode; _null_ if no specific one is available.
     * @param context the context in which the encoding happens; _null_ if none is available.
     * @return best dictionary to use for encoding; _null_ if none is available.
     */
    fun getEncodingDictionary(feature: Any? = null, context: Any? = null): JbDictionary? = null

    /**
     * The default dictionary to use for encoding.
     * @return default dictionary to use for encoding; _null_ if none is available.
     */
    @Deprecated(
        message = "Please use getEncodingDictionary",
        replaceWith = ReplaceWith("getEncodingDictionary(feature)"),
        level = DeprecationLevel.WARNING
    )
    fun defaultDict(): String? = getEncodingDictionary()?.id()
}