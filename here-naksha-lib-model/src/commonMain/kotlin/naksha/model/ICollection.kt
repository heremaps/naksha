@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * Abstract interface to a collection administrative object.
 *
 * When you need to manage a collection, you should create a [naksha.model.request.WriteRequest].
 *
 * The [ITupleCodec] of a collection normally ignores the context, because the context is quite clear.
 * @since 3.0.0
 */
@v30_experimental
@JsExport
interface ICollection : ITupleCodec {
    /**
     * The map in which the collection is located.
     * @since 3.0.0
     */
    val map: IMap

    /**
     * The identifier of the collection.
     * @since 3.0.0
     */
    val id: String

    /**
     * The collection-number.
     * - Throws [NakshaError.COLLECTION_NOT_FOUND] if the collection does not exist.
     * @since 3.0.0
     */
    val number: Int

    /**
     * The latest known state (HEAD state).
     * @since 3.0.0
     */
    val nakshaCollection: NakshaCollection

    /**
     * Tests if this collection exists.
     * @param session the session to use to query the storage; _null_ if an internal admin-session should be used.
     * @return _true_ if the collection exists.
     * @since 3.0.0
     */
    fun exists(session: ISession? = null): Boolean

    // TODO: Add:
    //       fun create(collection: NakshaCollection, session: IWriteSession)
    //       fun delete(session: IWriteSession)
}