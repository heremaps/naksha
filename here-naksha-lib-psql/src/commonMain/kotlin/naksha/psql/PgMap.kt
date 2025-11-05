@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.Naksha
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_NUMBER
import naksha.model.Naksha.NakshaCompanion.DICTIONARIES_COL
import naksha.model.Naksha.NakshaCompanion.DICTIONARIES_COL_NUMBER
import naksha.model.Naksha.NakshaCompanion.MAPS_COL
import naksha.model.Naksha.NakshaCompanion.MAPS_COL_NUMBER
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_NUMBER
import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.psql.PgColumn.PgColumnCompanion.allColumns
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A map stores collections.
 */
@JsExport
open class PgMap internal constructor(
    /**
     * The reference to the storage.
     * @since 3.0.0
     */
    open val storage: PgStorage,

    /**
     * The HEAD state of the map.
     * @since 3.0.0
     */
    nakshaMap: NakshaMap,

    /**
     * The map-id.
     * @since 3.0
     */
    val id: String = nakshaMap.id,

    /**
     * The map-number.
     * @since 3.0
     */
    val number: Int = nakshaMap.number
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
     * If the map is deleted, this value stays unmodified, because the [PgMap] will be removed from caching. However, if only the _HEAD_ state of the map is modified, so basically an `UPDATE` is done, the _HEAD_ reference is replaced on-the-fly.
     * @since 3.0
     */
    val headRef = AtomicNonNullRef(nakshaMap)

    /**
     * Reads [headRef].
     * @see [headRef]
     * @since 3.0
     */
    val head: NakshaMap
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
                c = PgCollection(this, NakshaCollection().withMapId(id).withId(COLLECTIONS_COL))
                _collections = c
            }
            return c
        }

    protected val collectionCache = AtomicMap<Int, PgCollection>()
    protected val collectionNumberById = AtomicMap<String, Int>()

    protected fun storeCollection(collection: PgCollection) {
        collectionNumberById[collection.id] = collection.number
        // TODO: Improve this, we should keep the PgCollection that has the higher version!
        collectionCache[collection.number] = collection
    }

    fun invalidateCollection(collection: PgCollection) {
        collectionCache.remove(collection.number, collection)
        //collectionNumberById.remove(collection.id, collection.number)
    }

    /**
     * Returns the `search_path` so that this map is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     * @return the `search_path` so that this map is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     */
    fun getSearchPath(): String = if (this is PgAdminMap) {
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
        val indices: List<PgIndex>
        val indexNames = collection.head.indices ?: PgIndex.DEFAULT_INDICES
        indices = mutableListOf()
        for (indexName in indexNames) {
            if (indexName == null) continue
            val index = PgIndex.of(indexName.toString())
            if (index != null
                && !indices.contains(index)
                && !index.internal) {
                indices.add(index)
            }
        }
        val NOW = Epoch()

        if (collection is PgNakshaTransactions) {
            val txn = PgTransactions(collection)
            txn.create(conn)
            txn.createYear(conn, NOW.year)
            txn.createYear(conn, NOW.year + 1)
            //txn.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
            txn.createIndex(conn, PgIndex.id_unique)
            txn.createIndex(conn, PgIndex.txn_unique)
            for (index in indices) {
                txn.createIndex(conn, index)
            }

            // We can have a meta table for transactions, but no history or deleted!
            if (collection.metaTable != null) {
                val meta = PgMeta(txn)
                meta.create(conn)
                //meta.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
                meta.createIndex(conn, PgIndex.id_unique)
                meta.createIndex(conn, PgIndex.version)
                for (index in indices) {
                    meta.createIndex(conn, index)
                }
            }
            return
        }

        val head = collection.headTable
        head.create(conn)
        //head.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
        head.createIndex(conn, PgIndex.id_unique)
        head.createIndex(conn, PgIndex.version)
        for (index in indices) {
            head.createIndex(conn, index)
        }

        val deleted = collection.deletedTable
        if (deleted != null) {
            deleted.create(conn)
            //deleted.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
            deleted.createIndex(conn, PgIndex.id_unique)
            deleted.createIndex(conn, PgIndex.version)
            for (index in indices) {
                deleted.createIndex(conn, index)
            }
        }

        val meta = collection.metaTable
        if (meta != null) {
            meta.create(conn)
            //meta.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
            meta.createIndex(conn, PgIndex.id_unique)
            meta.createIndex(conn, PgIndex.version)
            for (index in indices) {
                meta.createIndex(conn, index)
            }
        }

        val history = collection.historyTable
        if (history != null) {
            history.create(conn)
            history.createYear(conn, NOW.year)
            history.createYear(conn, NOW.year + 1)
            //history.createIndex(conn, PgIndex.tn_pkey) // PRIMARY KEY
            history.createIndex(conn, PgIndex.id)
            history.createIndex(conn, PgIndex.version)
            for (index in indices) {
                history.createIndex(conn, index)
            }
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
        val cursor = PgRelation.select(conn, collection.map.id, id)
        cursor.use {
            //
            // NOTE: We ignore all unknown relations, that allows users to add some own indices and relations!
            //
            var headRelation: PgRelation? = null
            val headIndices: MutableList<PgIndex> = mutableListOf()
            val headPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            val headYears: MutableMap<Int, PgRelation> = mutableMapOf()
            var deletedRelation: PgRelation? = null
            val deletedIndices: MutableList<PgIndex> = mutableListOf()
            val deletedPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            var historyRelation: PgRelation? = null
            val historyIndices: MutableList<PgIndex> = mutableListOf()
            val historyYears: MutableMap<Int, PgRelation> = mutableMapOf()
            val historyPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            var metaRelation: PgRelation? = null
            val metaIndices: MutableList<PgIndex> = mutableListOf()
            while (cursor.next()) {
                val rel = PgRelation(cursor)
                if (id == TRANSACTIONS_COL) {
                    // We know that the transaction table does only have a HEAD.
                    // We further know, that head is split yearly!
                    if (rel.isAnyHeadRelation()) {
                        if (rel.isHeadRootRelation()) {
                            headRelation = rel
                        } else if (rel.isTxnYearRelation()) {
                            val year = rel.year()
                            if (year > 0) headYears[year] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in headIndices) headIndices.add(index)
                        }
                    }
                } else {
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
                    if (rel.isAnyDeleteRelation()) {
                        if (rel.isDeleteRootRelation()) {
                            deletedRelation = rel
                        } else if (rel.isTable()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) deletedPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in deletedIndices) deletedIndices.add(index)
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
                    if (parts == 0 && headYears.isNotEmpty()) {
                        val txn = PgTransactions(this as PgNakshaTransactions)
                        for (entry in historyYears) txn.years[entry.key] = PgTransactionsYear(txn, entry.key)
                        headTable = txn
                    } else {
                        if (parts < 2 || parts > 256) {
                            throw NakshaException(
                                ILLEGAL_STATE,
                                "Invalid amount of HEAD partitions found, must be 2..256, but is ${headPartitions.size}"
                            )
                        }
                        collection.headTable = PgHead(collection, headRelation.storageClass, parts)
                    }
                } else {
                    collection.headTable = PgHead(collection, headRelation.storageClass, 0)
                }
                for (index in headIndices) collection.headTable.addIndex(index)
            }
            if (historyRelation != null) {
                val history = PgHistory(collection.headTable)
                collection.historyTable = history
                for (entry in historyYears) history.years[entry.key] = PgHistoryYear(history, entry.key)
            }
            if (deletedRelation != null) {
                val deleted = PgDeleted(collection.headTable)
                collection.deletedTable = deleted
                for (index in deletedIndices) deleted.addIndex(index)
            }
            if (metaRelation != null) {
                val meta = PgMeta(collection.headTable)
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
        val deleted = collection.deletedTable
        if (deleted != null) builder.append("DROP TABLE IF EXISTS ${deleted.quotedName} CASCADE;\n")
        val meta = collection.metaTable
        if (meta != null) builder.append("DROP TABLE IF EXISTS ${meta.quotedName} CASCADE;\n")
        val history = collection.historyTable
        if (history != null) builder.append("DROP TABLE IF EXISTS ${history.quotedName} CASCADE;\n")
        val SQL = builder.toString()
        logger.info("Dropped collection {}@{}", collection.id, collection.number)
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
        if (this is PgAdminMap) {
            return when (id) {
                COLLECTIONS_COL -> collections
                TRANSACTIONS_COL -> transactions
                MAPS_COL -> maps
                DICTIONARIES_COL -> dictionaries
                else -> null
            }
        }
        if (id == COLLECTIONS_COL) return collections
        val number = collectionNumberById[id]
        val existing = if (number != null) collectionCache[number] else null
        if (existing != null || conn == null) return existing

        // Read from database
        val outRows = PgColumnRows()
            .withStorageNumber(storage.number)
            .withMapNumber(this.number)
            .withCollectionNumber(COLLECTIONS_COL_NUMBER)
            .addColumns(allColumns)
        setSearchPath(conn)
        val SQL = """SELECT ${outRows.names()}
FROM ${collections.headTable.quotedName}
WHERE id = $1"""
        val plan = conn.prepare(SQL, arrayOf(PgType.STRING.text))
        plan.execute(arrayOf(id)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = Naksha.decodeTuple(tuple).proxy(NakshaCollection::class)
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
        if (this is PgAdminMap) {
            return when (number) {
                COLLECTIONS_COL_NUMBER -> collections
                TRANSACTIONS_COL_NUMBER -> transactions
                MAPS_COL_NUMBER -> maps
                DICTIONARIES_COL_NUMBER -> dictionaries
                else -> null
            }
        }
        if (number == COLLECTIONS_COL_NUMBER) return collections
        val existing = collectionCache[number]
        if (existing != null || conn == null) return existing

        // Read from database
        val outRows = PgColumnRows()
            .withStorageNumber(storage.number)
            .withMapNumber(this.number)
            .withCollectionNumber(COLLECTIONS_COL_NUMBER)
            .addColumns(allColumns)
        setSearchPath(conn)
        val SQL = """SELECT ${outRows.names()}
FROM ${collections.headTable.quotedName}
WHERE naksha_tn_feature_number(tn) = $1"""
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        plan.execute(arrayOf(number)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaCollection = Naksha.decodeTuple(tuple).proxy(NakshaCollection::class)
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
    fun listPgCollections(conn: PgConnection, map: PgMap): PgCollectionList {
        val list = PgCollectionList()
        // TODO: Implement me!
        return list
    }
}
