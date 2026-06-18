@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.Action
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
import naksha.model.TupleNumber
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
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
    protected fun storeCollection(collection: PgCollection) {
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
     * Returns the `search_path` so that this map is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     * @return the `search_path` so that this map is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     */
    fun getSearchPath(): String = if (this is PgAdminCatalog) {
        "SET search_path = \"naksha~admin\", topology, hint_plan, public"
    } else {
        "SET search_path = ${quotedId}, \"naksha~admin\", topology, hint_plan, public"
    }

    /**
     * Sets the `search_path` for the current transaction, so until `commit` or `rollback`.
     * @param conn the connection where to set the search path.
     * @since 3.0
     * @see [getSearchPath]
     */
    fun setSearchPath(conn: PgConnection) {
        conn.execute(getSearchPath()).close()
    }

    /**
     * Create a new [collection][PgCollection] using the given connection, and return it.
     *
     * ### Note
     * - This method does not commit the given connection, therefore the collection is not yet persisted, but can be used through the given connection.
     * - The method does not insert the corresponding entry into the collection's collection, this must be done upfront by the caller.
     *
     * - Throws [NakshaError.MAP_NOT_FOUND] if the given map does not exist _(anymore)_.
     * - Throws [NakshaError.COLLECTION_EXISTS] if such a collection exists already in the given map.
     * @param conn the connection to use to access the database.
     * @param collection the collection to create.
     * @return the created map.
     * @since 3.0
     */
    open fun createPgCollection(conn: PgConnection, collection: PgCollection) {
        // Ensure that all tables and indices are created in the correct map!
        setSearchPath(conn)
        val NOW = Epoch()

        // The indices list drives which optional indices are created:
        //   - null  → backward-compatible: all DEFAULT_INDICES (non-internal), but only when members is also null
        //             (when members is explicitly set, only custom indices from the indices list are created)
        //   - non-null → only the client-declared (already stripped of internal entries by normalizeCollection)
        // Mandatory/internal indices (id_unique, txn_unique, id, version, gbn) are always created below
        // and are not present in either list.
        val optionalIndices = collection.head.indices
        val membersExplicit = collection.head.members != null
        val defaultPgIndices: List<PgIndex> = if (optionalIndices == null && !membersExplicit) PgIndex.DEFAULT_INDICES.filter { !it.internal } else emptyList()

        /** Creates one optional index on [table]: delegates to createIndex for known PgIndex names,
         *  or to createCustomIndex for user-defined names. */
        fun createOptionalIndex(table: PgTable, idx: naksha.model.objects.Index) {
            val pgIdx = PgIndex.of(idx.name)
            if (pgIdx != null) table.createIndex(conn, pgIdx)
            else table.createCustomIndex(conn, idx)
        }

        val head = collection.headTable
        head.create(conn)
        head.createIndex(conn, PgIndex.id_unique)
        head.createIndex(conn, PgIndex.version)
        head.createIndex(conn, PgIndex.gbn_idx)
        for (index in defaultPgIndices) head.createIndex(conn, index)
        if (optionalIndices != null) for (idx in optionalIndices) if (idx != null) createOptionalIndex(head, idx)

        val meta = collection.metaTable
        if (meta != null) {
            meta.create(conn)
            meta.createIndex(conn, PgIndex.id_unique)
            meta.createIndex(conn, PgIndex.version)
            meta.createIndex(conn, PgIndex.gbn_idx)
            for (index in defaultPgIndices) meta.createIndex(conn, index)
            if (optionalIndices != null) for (idx in optionalIndices) if (idx != null) createOptionalIndex(meta, idx)
        }

        val history = collection.historyTable
        if (history != null) {
            history.create(conn)
            history.createPartition(conn, NOW.year)
            history.createPartition(conn, NOW.year + 1)
            history.createIndex(conn, PgIndex.id)
            history.createIndex(conn, PgIndex.version)
            history.createIndex(conn, PgIndex.gbn_idx)
            for (index in defaultPgIndices) history.createIndex(conn, index)
            if (optionalIndices != null) for (idx in optionalIndices) if (idx != null) createOptionalIndex(history, idx)
        }
        invalidateCollection(collection)
    }

    /**
     * Refresh the cached information of this collection, mainly updates the history tables.
     * - Throws [NakshaError.COLLECTION_NOT_FOUND], if the collection has been deleted.
     * @param conn the connection to query the database; if _null_, a new data connection is acquired, used, and released.
     * @since 3.0.0
     */
    private fun refreshPgCollection(conn: PgConnection, collection: PgCollection): PgCollection {
        // TODO: Fix me!
        val cursor = PgRelation.select(conn, collection.catalog.id, id)
        cursor.use {
            //
            // NOTE: We ignore all unknown relations, that allows users to add some own indices and relations!
            //
            var headRelation: PgRelation? = null
            val headIndices: MutableList<PgIndex> = mutableListOf()
            val headPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            val headYears: MutableMap<Int, PgRelation> = mutableMapOf()
            var historyRelation: PgRelation? = null
            val historyIndices: MutableList<PgIndex> = mutableListOf()
            val historyYears: MutableMap<Int, PgRelation> = mutableMapOf()
            val historyPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            var metaRelation: PgRelation? = null
            val metaIndices: MutableList<PgIndex> = mutableListOf()
            while (cursor.next()) {
                val rel = PgRelation(cursor)
                    if (rel.isAnyHeadRelation()) {
                        if (rel.isHeadRootRelation()) {
                            headRelation = rel
                        } else if (rel.isTable()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) headPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in headIndices) headIndices.add(index)
                        }
                    }
                    if (rel.isAnyHistoryRelation()) {
                        if (rel.isHistoryRootRelation()) {
                            historyRelation = rel
                        } else if (rel.isHistoryYearRelation()) {
                            val year = rel.year()
                            if (year > 0) historyYears[year] = rel
                        } else if (rel.isHistoryPartition()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) historyPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in historyIndices) historyIndices.add(index)
                        }
                    }
                if (rel.isAnyMetaRelation()) {
                    if (rel.isMetaRootRelation()) {
                        metaRelation = rel
                    } else if (rel.isIndex()) {
                        val index = PgIndex.of(rel.name)
                        if (index != null && index !in metaIndices) metaIndices.add(index)
                    }
                }
            }

            if (headRelation != null) {
                if (headRelation.isPartition()) {
                    val parts = headPartitions.size
                    if (parts < 2 || parts > 256) {
                        throw NakshaException(
                            ILLEGAL_STATE,
                            "Invalid amount of HEAD partitions found, must be 2..256, but is ${headPartitions.size}"
                        )
                    }
                    collection.headTable = PgHeadTable(collection, headRelation.storageClass, parts)
                } else {
                    collection.headTable = PgHeadTable(collection, headRelation.storageClass, 0)
                }
                for (index in headIndices) collection.headTable.addIndex(index)
            }
            if (historyRelation != null) {
                val history = PgHistoryTable(collection.headTable)
                collection.historyTable = history
                for (entry in historyYears) history.years[entry.key] = PgHistoryYear(history, entry.key)
            }
            if (metaRelation != null) {
                val meta = PgMetaTable(collection.headTable)
                collection.metaTable = meta
                for (index in metaIndices) meta.addIndex(index)
            }
        }
        return collection
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
        val meta = collection.metaTable
        if (meta != null) builder.append("DROP TABLE IF EXISTS ${meta.quotedName} CASCADE;\n")
        val history = collection.historyTable
        if (history != null) builder.append("DROP TABLE IF EXISTS ${history.quotedName} CASCADE;\n")
        val SQL = builder.toString()
        logger.info("Dropped collection '{}' with collection-number {}", collection.id, collection.head.collectionNumber)
        conn.execute(SQL).close()
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
        val outRows = PgRows().withCollection(collections.head)
        setSearchPath(conn)
        val SQL = """SELECT ${outRows.aliases()}
FROM ${collections.headTable.quotedName}
WHERE id = $1 AND (version & 3) < 2"""
        val plan = conn.prepare(SQL, arrayOf(PgType.STRING.text))
        plan.execute(arrayOf(id)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = tuple.decodeFeature(null).proxy(NakshaCollection::class)
        val pgCollection = PgCollection(this, nakshaCollection)
        storeCollection(pgCollection)
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
        val outRows = PgRows().useHeadTable().withCollection(collections.head)
        setSearchPath(conn)
        val SQL = """SELECT ${outRows.names()}
FROM ${collections.headTable.quotedName}
WHERE fn = $1 AND (version & 3) < 2"""
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        plan.execute(arrayOf(number)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = tuple.decodeFeature(null).proxy(NakshaCollection::class)
        val pgCollection = PgCollection(this, nakshaCollection)
        storeCollection(pgCollection)
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
