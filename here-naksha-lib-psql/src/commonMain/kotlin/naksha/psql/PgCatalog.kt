@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.Action
import naksha.model.IWriteSession
import naksha.model.Naksha
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_ID
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_FN
import naksha.model.Naksha.NakshaCompanion.BOOKS_COL_ID
import naksha.model.Naksha.NakshaCompanion.BOOKS_COL_FN
import naksha.model.Naksha.NakshaCompanion.CATALOGS_COL_ID
import naksha.model.Naksha.NakshaCompanion.CATALOGS_COL_FN
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_ID
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_FN
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.PgTx
import naksha.model.TupleNumber
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.XyzMembers
import naksha.model.objects.XyzProcessors
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A map _(aka catalog)_ contains collections.
 * @since 3.0
 */
@JsExport
open class PgCatalog internal constructor(
    /**
     * The reference to the storage (effectively the same as the database, for now).
     * @since 3.0.0
     */
    open val storage: PgStorage,

    /**
     * The HEAD state of the catalog.
     * @since 3.0.0
     */
    nakshaCatalog: NakshaCatalog,

    /**
     * The custom catalog-identifier.
     * @since 3.0
     */
    val id: String = nakshaCatalog.id,

    /**
     * The catalog-number of the catalog, actually the same as the feature-number of the [NakshaCatalog] feature.
     * @since 3.0
     */
    val catalogNumber: Int = Naksha.catalogNumber(id),
) {
    /**
     * The map-identifier quoted optionally in double quotes.
     * @since 3.0
     */
    @JvmField
    val quotedId = quoteIdent(id)

    /**
     * The _HEAD_ state of the map.
     *
     * ### Note
     * If the map is deleted, this value stays unmodified, because the [PgCatalog] will be removed from caching. However, if only the _HEAD_ state of the map is modified, so basically an `UPDATE` is done, the _HEAD_ reference is replaced on-the-fly.
     * @since 3.0
     */
    val headRef = AtomicNonNullRef(nakshaCatalog)

    /**
     * Reads [headRef].
     * @see [headRef]
     * @since 3.0
     */
    val head: NakshaCatalog
        get() = headRef.get()

    private var _collections: PgCollection? = null

    /**
     * The collection's collection of the map _(`naksha~collections` aka `0`)_.
     * @since 3.0
     * @see [createPgCollection]
     * @see [getPgCollectionById]
     * @see [getPgCollectionByNumber]
     * @see [deletePgCollection]
     */
    val collections: PgCollection
        get() {
            var c = _collections
            if (c == null) {
                val nakshaCollection = NakshaCollection(COLLECTIONS_COL_ID, id)
                    .withXyzMembers()
                    .withXyzIndices()
                c = PgCollection(this, nakshaCollection)
                val collectionsColNumber = c.collectionNumber
                nakshaCollection.tupleNumber = TupleNumber(
                    storage.number, catalogNumber, collectionsColNumber,
                    Int64(collectionsColNumber.toLong()), Int64(1)
                )
                _collections = c
            }
            return c
        }

    protected val collectionCache = AtomicMap<Int, PgCollection>()

    /**
     * Store the given [PgCollection] into the cache.
     * @param collection the collection to store, must have a valid _HEAD_ state **and** must have a valid [TupleNumber].
     * @since 3.0
     */
    protected fun cacheCollection(collection: PgCollection) {
        do {
            val id = collection.id
            val newCollection = collection.head
            val collectionNumber = newCollection.collectionNumber
            val newVersion = newCollection.tupleNumber?.version ?: throw NakshaException(
                ILLEGAL_STATE,
                "Cannot store collection '$id' in cache, missing `tupleNumber`"
            )

            val existing = collectionCache[collectionNumber]
            val existingTn = existing?.head?.tupleNumber
            val existingVersion: Int64? = if (existingTn != null && Action.fromVersion(existingTn.version) != Action.DELETE) existingTn.version else null
            if (existingVersion != null && existingVersion > newVersion) {
                logger.debug("Do not update collection '$id', the existing version ($existingVersion) is newer than the new ($newVersion)")
                break
            }
            if (existing != null) {
                if (collectionCache.replace(collectionNumber, existing, collection)) break
            } else {
                if (collectionCache.putIfAbsent(collectionNumber, collection) == null) break
            }
        } while (true)
    }

    internal fun invalidateCollection(collection: PgCollection) {
        collectionCache.remove(collection.head.collectionNumber, collection)
    }

    /**
     * Returns the `search_path` so that this schema _(catalog)_ is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     * @return the `search_path` so that this schema _(catalog)_ is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     */
    fun searchPath(): String = if (this is PgAdminCatalog) {
        "\"naksha~admin\", topology, hint_plan, public"
    } else {
        "${quotedId}, \"naksha~admin\", topology, hint_plan, public"
    }

    /**
     * Sets the `search_path` for the current transaction, so until `commit` or `rollback`.
     *
     * Actually:
     * ```sql
     * SET search_path = ${searchPath()}
     * ```
     *
     * @param conn the connection where to set the search path.
     * @since 3.0
     * @see [searchPath]
     */
    fun setSearchPath(conn: PgConnection) {
        conn.execute("SET search_path = ${searchPath()}").close()
    }

    /**
     * Create a new [collection][PgCollection] using the given connection, and return it.
     *
     * ### Note
     * - This method does not commit the given connection, therefore the collection is not yet persisted, but can be used through the given connection.
     * - The method does not insert the corresponding entry into the collection's collection, this must be done upfront by the caller.
     * @param conn the connection to use to access the database.
     * @param collection the collection to create.
     * @return the created map.
     * @since 3.0
     */
    open fun createPgCollection(conn: PgConnection, collection: PgCollection) {
        // Ensure that all tables and indices are created in the correct schema!
        setSearchPath(conn)

        val headTable = collection.headTable
        headTable.create(conn)
        for (index in collection.headIndices) headTable.createIndex(conn, index)

        if (collection.storeHistory) {
            val history = collection.historyTable
            history.create(conn)
            // Register optional indices before PgWriter seeds the year partitions. New partitions inherit
            // every index registered on the history root.
            for (index in collection.historyIndices) history.addIndex(index)
        }

        // TODO: Fix cache by adding 2nd level cache in session, we only want to update in 2nd level cache and move to 1rst level when committed!
        invalidateCollection(collection)
    }

    /**
     * Refresh the cached information of this collection, mainly updates the history tables.
     * - Throws [NakshaError.COLLECTION_NOT_FOUND], if the collection has been deleted.
     * @param conn the connection to query the database; if _null_, a new data connection is acquired, used, and released.
     * @since 3.0.0
     */
    private fun refreshPgCollection(conn: PgConnection, collection: PgCollection): PgCollection {
        // TODO: Implement me, but only if needed!
        throw UnsupportedOperationException()
    }

    /**
     * Deletes a collection.
     * @param conn the connection to use to access the database.
     * @param collection the collection to delete.
     * @since 3.0.0
     */
    open fun deletePgCollection(conn: PgConnection, collection: PgCollection) {
        setSearchPath(conn)
        val builder = StringBuilder()
        val head = collection.headTable
        builder.append("DROP TABLE IF EXISTS ${head.quotedName} CASCADE;\n")

        val history = collection.historyTable
        builder.append("DROP TABLE IF EXISTS ${history.quotedName} CASCADE;\n")

        val SQL = builder.toString()
        conn.execute(SQL).close()
        logger.info("Dropped collection '{}' with collection-number {}", collection.id, collection.head.collectionNumber)

        // TODO: Fix cache by adding 2nd level cache in session, we only want to update in 2nd level cache and move to 1rst level when committed!
        invalidateCollection(collection)
    }

    /**
     * Returns the existing collection with the given identifier; if any.
     * @param conn the connection to use to access the database.
     * @param id the collection-id to query.
     * @return the collection, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    fun getPgCollectionById(conn: PgConnection?, id: String): PgCollection? {
        if (this is PgAdminCatalog) {
            return when (id) {
                COLLECTIONS_COL_ID -> collections
                TRANSACTIONS_COL_ID -> transactions
                CATALOGS_COL_ID -> catalogs
                BOOKS_COL_ID -> books
                else -> null
            }
        }
        if (id == COLLECTIONS_COL_ID) return collections
        val collectionNumber = Naksha.collectionNumber(id)
        val existing = collectionCache[collectionNumber]
        if (existing != null || conn == null) return existing

        // Read from database
        setSearchPath(conn)
        val TABLE = collections.headTable.quotedName
        val ID = collections.column(Id)
        val SQL = "SELECT * FROM $TABLE WHERE $ID = $1"
        val plan = conn.prepare(SQL, arrayOf(PgType.STRING.text))
        val rows = PgRows().withCollection(collections)
        plan.execute(arrayOf(id)).fetch().use {
            rows.readAll(cursor = it)
        }
        if (rows.size == 0) return null
        val tuple = rows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = tuple.decodeFeature(null).proxy(NakshaCollection::class)
        nakshaCollection.tupleNumber = tuple.tupleNumber
        val pgCollection = PgCollection(this, nakshaCollection)
        // TODO: Fix cache by adding 2nd level cache in session, we only want to update in 2nd level cache and move to 1rst level when committed!
        cacheCollection(pgCollection)
        return pgCollection
    }

    /**
     * Returns the existing collection with the given number; if any.
     * @param conn the connection to use to access the database.
     * @param number the collection-number to query.
     * @return the collection, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    fun getPgCollectionByNumber(conn: PgConnection?, number: Int): PgCollection? {
        if (this is PgAdminCatalog) {
            return when (number) {
                COLLECTIONS_COL_FN -> collections
                TRANSACTIONS_COL_FN -> transactions
                CATALOGS_COL_FN -> catalogs
                BOOKS_COL_FN -> books
                else -> null
            }
        }
        if (number == COLLECTIONS_COL_FN) return collections
        val existing = collectionCache[number]
        if (existing != null || conn == null) return existing

        // Read from database
        setSearchPath(conn)
        val TABLE = collections.headTable.quotedName
        val SQL = "SELECT * FROM $TABLE WHERE $FN = $1"
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        val rows = PgRows().withCollection(collections)
        plan.execute(arrayOf(number)).fetch().use { rows.readAll(it) }
        if (rows.size == 0) return null
        val tuple = rows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = tuple.decodeFeature(null).proxy(NakshaCollection::class)
        nakshaCollection.tupleNumber = tuple.tupleNumber
        val pgCollection = PgCollection(this, nakshaCollection)
        // TODO: Fix cache by adding 2nd level cache in session, we only want to update in 2nd level cache and move to 1rst level when committed!
        cacheCollection(pgCollection)
        return pgCollection
    }

    /**
     * Returns a list of all existing collections in the map, excluding the collections' collection.
     * @param conn the connection to use to access the database.
     * @param map the map in which to search for the collection.
     * @return the list of existing collections, _(empty, when no collections exist)_.
     * @since 3.0.0
     */
    fun listPgCollections(conn: PgConnection, map: PgCatalog): PgCollectionList {
        val list = PgCollectionList()
        // TODO: Implement me!
        return list
    }
}
