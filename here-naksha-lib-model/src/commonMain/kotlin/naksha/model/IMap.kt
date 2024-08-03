@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
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
}