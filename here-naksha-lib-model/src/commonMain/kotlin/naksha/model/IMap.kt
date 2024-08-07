@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictManager
import naksha.jbon.JbDictManager
import naksha.jbon.JbDictionary
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport

/**
 * Abstract interface to a map administrative object.
 */
@JsExport
interface IMap {
    /**
     * The storage in which the collection is located.
     */
    val storage: IStorage

    /**
     * The identifier of the map.
     */
    val id: String

    /**
     * The map-number, a value between 0 and 4095 (_2^12-1_).
     */
    val number: Int

    /**
     * Returns the collection admin object, for the collection with the given identifier.
     * @param collectionId the collection-identifier.
     * @return the collection admin object.
     */
    operator fun get(collectionId: String): ICollection

    /**
     * Returns the collection-identifier for the given collection-number.
     * @param collectionNumber the collection-number.
     * @return the collection-identifier or _null_, if no such collection exists.
     */
    fun getCollectionId(collectionNumber: Int64): String?

    /**
     * Tests if this map exists.
     * @return _true_ if the map exists.
     */
    fun exists(): Boolean

    /**
     * The dictionary manager of the map to decode features read from this map.
     * @since 3.0.0
     */
    val dictManager: IDictManager

    /**
     * Returns the dictionary to use to encode the given feature.
     *
     * @param collectionId the collection for which to return the default encoding dictionary.
     * @param feature the feature that should be encoded, may have an impact on the selected dictionary, but is optional.
     * @return the encoding dictionary for the collection; if any.
     * @since 3.0.0
     */
    fun encodingDict(collectionId: String, feature: NakshaFeature? = null): JbDictionary?

}