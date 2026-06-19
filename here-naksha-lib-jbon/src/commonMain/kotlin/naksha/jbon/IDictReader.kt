@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import kotlin.js.JsExport

/**
 * A dictionary reader.
 * @since 3.0
 */
@JsExport
@Deprecated("To be removed")
interface IDictReader {
    /**
     * Retrieve the dictionary with the given identifier.
     * @param id The dictionary identifier to lookup.
     * @return The global dictionary with the given identifier; _null_ when no such dictionary exists.
     * @since 3.0
     */
    fun getDictionary(id: String): JbDictionary?

    /**
     * The best dictionary to encode the given feature.
     * @param feature the feature to encode; _null_ if no specific one is available.
     * @param context the context in which the encoding happens (for example the map or collection); _null_ if none is available.
     * @return best dictionary to use for encoding; _null_ if none is available.
     * @since 3.0
     */
    fun getEncodingDictionary(feature: Any?, context: Any? = null): JbDictionary? = null

}