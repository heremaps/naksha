@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.jbon.IDictManager
import kotlin.js.JsExport

/**
 * Abstract interface to a map administrative object.
 *
 * The [IDictManager] of a map normally accepts in [getEncodingDictionary(feature, context)][IDictManager.getEncodingDictionary] either an [ICollection], the [number][ICollection.number], or the [id][ICollection.id] of the collection as context.
 * @since 3.0.0
 */
@v30_experimental
@JsExport
interface IMap : IDictManager {
    /**
     * The storage in which the map is located.
     * @since 3.0.0
     */
    val storage: IStorage

    /**
     * The identifier of the map.
     * @since 3.0.0
     */
    val id: String

    /**
     * The map-number, needed for example in [StoreNumber].
     * - Throws [NakshaError.MAP_NOT_FOUND] if the map does not exist.
     * @since 3.0.0
     */
    val number: Int

    /**
     * Returns the admin object for the collection with the given identifier.
     *
     * Does not perform network operations. If no such collection is yet loaded from storage, creates a virtual admin object, that allows the management of the collection, like to query if such a collection exists already, or to create the collection. Creating a collection will keep it in the cache, and grant it a unique collection-number, provided by the storage.
     * @param collectionId the collection-identifier.
     * @return the collection admin object.
     * @since 3.0.0
     */
    operator fun get(collectionId: String): ICollection

    /**
     * Returns the admin object for the collection with the given number.
     *
     * The method will perform network operations, when it has yet no knowledge about a collection with such a number.
     * @param collectionNumber the collection-number.
     * @param session the session to query the storage; _null_ if an internal admin-session should be used.
     * @return the collection admin object; _null_ if no such collection exists.
     * @since 3.0.0
     */
    fun getCollection(collectionNumber: Int, session: ISession? = null): ICollection?

    /**
     * Returns the collection-identifier for the given collection-number.
     *
     * The method will perform network operations, when it has yet no knowledge about a collection with such a number.
     * @param collectionNumber the collection-number.
     * @param session the session to query the storage; _null_ if an internal admin-session should be used.
     * @return the collection-identifier or _null_, if no such collection exists.
     * @since 3.0.0
     */
    fun getCollectionId(collectionNumber: Int, session: ISession? = null): String?

    /**
     * Tests if this map exists.
     * @param session the session to use to query the storage; _null_ if an internal admin-session should be used.
     * @return _true_ if the map exists.
     * @since 3.0.0
     */
    fun exists(session: ISession? = null): Boolean

    /**
     * Create the map, if it does not yet exist.
     * @param session the session to use to create the map in the storage; _null_ if an internal admin-session should be used, with auto-commit.
     * @since 3.0.0
     */
    fun create(session: IWriteSession? = null)

    /**
     * Delete the map and all collection within it.
     * @param session the session to use to create the map in the storage; _null_ if an internal admin-session should be used, with auto-commit.
     * @since 3.0.0
     */
    fun delete(session: IWriteSession? = null)
}