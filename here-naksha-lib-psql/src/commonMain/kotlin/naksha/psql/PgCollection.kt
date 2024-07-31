package naksha.psql

import naksha.base.AtomicRef
import naksha.base.Epoch
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.currentMillis
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil.PlatformUtilCompanion.HOUR
import naksha.base.PlatformUtil.PlatformUtilCompanion.SECOND
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.psql.PgStorageClass.Companion.Consistent
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A collection is a set of database tables, that together form a logical feature store. This lower level implementation ensures that all collection information are cached, including statistical information, and that the cache is refreshed on demand from time to time.
 *
 * Additionally, this implementation supports methods to create new collections (so the whole set of tables), or to refresh the information about the collection, to add or remove indices at runtime.
 *
 * This table should only be used in combination with [NakshaCollection][_nakshaCollection.model.objects.NakshaCollection] and is build according to the data stored in the feature. Actually, clients do operate on the collection features, and the `lib-psql` internally modifies the `PgCollection` accordingly to the external instructions. So, this class is a low level helper, and should only be used with great care, when directly using `lib-psql`.
 *
 * @since 3.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgCollection internal constructor(
    /**
     * The schema in which the collection is located.
     * @since 3.0.0
     */
    @JvmField val schema: PgSchema,
    /**
     * The unique identifier of the collection in the schema.
     * @since 3.0.0
     */
    @JvmField val id: String
) {
    /**
     * The map in which this collection is located. This is an alias for `schema.map`.
     */
    val map: String
        get() = schema.map

    private val _nakshaCollection = AtomicRef<NakshaCollection>(null)

    /**
     * The reference to the latest [NakshaCollection].
     */
    val nakshaCollection: NakshaCollection
        get() {
            if (!exists()) throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id)
            val c = _nakshaCollection.get() ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id)
            return c
        }

    /**
     * A lock internally used to synchronize access, like cache refresh.
     */
    protected val lock = Platform.newLock()

    /**
     * The epoch milliseconds when to automatically refresh the cache. If _null_, it needs to be refreshed instantly. Normally, we only refresh ones a day and rather rely upon PostgresQL notifications to do forced updates.
     */
    private var _updateAt: Int64? = null

    /**
     * Caches the information if the collection exists.
     */
    private var _exists: Boolean = false

    /**
     * Tests if this collection does exits.
     * @param connection the connection to use to query the database; _null_ if a new connection should be used.
     * @return _true_ if this collection does exist.
     */
    fun exists(connection: PgConnection? = null): Boolean {
        refresh(connection)
        return _exists
    }

    /**
     * The storage class of the collection.
     */
    var storageClass: PgStorageClass = PgStorageClass.Unknown
        private set

    /**
     * The amount of performance partitions.
     */
    var partitions: Int = 1
        private set

    /**
     * The field of [head].
     */
    private var _head: PgHead? = null

    /**
     * The `HEAD` table, so where to store features or transactions into.
     *
     * If this is an ordinary table, the is can be performance partitioned using [PgPlatform.partitionNumber] above the `feature.id`.
     *
     * If this is a `TRANSACTION` table, then the partitioning is done by [_nakshaCollection.model.Version.year].
     *
     * Writing directly into partitions, or reading from them, is discouraged, but in some cases necessary to improve performance drastically. In AWS the speed of every [single-flow](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html) connection is limited to 5 Gbps (10 Gbps when being in the same [cluster placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster)), but still always limited. When the PostgresQL database and the client both have higher bandwidth, then multiple parallel connection need to be used, for example to saturate the HERE temporary or consistent store bandwidth of 200 Gbps, between 20 and 40 connections are needed.
     *
     * **Notes**:
     * - The `TRANSACTION` table is not designed for ultra-high throughput, but rather as a storage that allows easy and fast garbage collection and to query ordered by transaction numbers or _sequence numbers_.
     * - Internal tables do not allow to add or remove indices, while ordinary consumer tables do allow this.
     */
    val head: PgHead
        get() {
            if (!exists()) throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id)
            return _head ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id)
        }

    /**
     * The history can be disabled fully or temporary. When disabled fully, no history tables are created, in that case this property will be _null_.
     *
     * If the history tables were created, they are always partitioned by the year of `txn_next`. Each yearly table is partitioned again the same way that [HEAD][head] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the history. The history therefore managed the same way as [HEAD][head], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     */
    var history: PgHistory? = null
        get() {
            if (!exists()) throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id)
            return field
        }
        private set

    /**
     * The deletion table can be disabled fully or temporary. When disabled fully, no deletion tables are created, in that case this property will be _null_.
     *
     * If the deletion tables are created, deleted features (not being purged) will be copied into this shadow deletion table. The deletion table is partitioned again the same way that [HEAD][head] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the deletion table. The deletion therefore managed the same way as [HEAD][head], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     */
    var deleted: PgDeleted? = null
        get() {
            check(exists()) { throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id) }
            return field
        }
        private set

    /**
     * An optional metadata table, never partitioned. This table is used to as internal storage for metadata, like statistics, calculated by background jobs and other information like this. It can be used as well by applications, and is accessible from outside, but does not have any history or track changes.
     */
    var meta: PgTable? = null
        get() {
            check(exists()) { throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id) }
            return field
        }
        private set

    /**
     * Tests if this is an internal collection. Internal collections have some limitation, for example it is not possible to add or drop indices, nor can they be created through the normal [create] method. They are basically immutable by design, but the content can be read and modified to some degree.
     *
     * **Warning**: Internal tables may have further limitations, even about the content, for example the transaction log only allows to mutate `tags` for normal external clients. Internal clients may perform other mutations, e.g. the internal _sequencer_ is allowed to set the `seqNumber`, `seqTs`, `geo` and `geo_ref` columns, additionally it will update the feature counts, when necessary. However, for normal clients the transaction log is immutable, and the _sequencer_ will only alter the transactions ones in their lifetime.
     */
    val internal: Boolean
        get() = id.startsWith("naksha~")

    /**
     * Create the collection, if it does not yet exist.
     *
     * The method does auto-commit, if no [connection] was given; otherwise committing must be done explicitly.
     * @param connection the connection to use to query the database; if _null_, then a new connection is used.
     * @param partitions the number of partitions to create, must be a value between 1 and 256.
     * @param storageClass the storage class to use.
     * @param storeHistory if the history should be stored, creating the corresponding history partitions.
     * @param storedDeleted if deleted features should be stored, creating the corresponding deletion partitions.
     * @param storeMeta if the meta-table should be created, which stores internal meta-data. Without this table, no statistics can be
     * acquired and collected.
     * @param indices the indices to add to all tables (the unique indices are always added by default).
     * @return either the existing collection or the created one.
     */
    open fun create(
        connection: PgConnection? = null,
        partitions: Int = 1,
        storageClass: PgStorageClass = Consistent,
        storeHistory: Boolean = true,
        storedDeleted: Boolean = true,
        storeMeta: Boolean = true,
        indices: List<PgIndex> = emptyList()
    ): PgCollection {
        check(!PgTable.isInternal(id)) {
            throw NakshaException(ILLEGAL_ARGUMENT, "It is not allowed to modify internal tables", id = id)
        }
        Naksha.verifyId(id)
        check(partitions in 1..256) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid amount of partitions requested, must be 0 to 256, was: $partitions")
        }
        return create_internal(connection, partitions, storageClass, storeHistory, storedDeleted, storeMeta, indices)
    }

    internal fun create_internal(
        connection: PgConnection?,
        partitions: Int,
        storageClass: PgStorageClass,
        storeHistory: Boolean,
        storedDeleted: Boolean,
        storeMeta: Boolean,
        indices: List<PgIndex>
    ): PgCollection {
        val s = schema.storage
        val conn = connection ?: s.newConnection(s.defaultOptions.copy(schema = schema.name, useMaster = true, readOnly = false))
        try {
            val NOW = Epoch()
            if (this.id == Naksha.VIRT_TRANSACTIONS) {
                val txn = PgTransactions(this)
                txn.create(conn)
                txn.createYear(conn, NOW.year)
                txn.createYear(conn, NOW.year + 1)
                txn.createIndex(conn, PgIndex.rowid_pkey)
                txn.createIndex(conn, PgIndex.txn_unique)
                for (index in indices) if (index != PgIndex.rowid_pkey && index != PgIndex.txn_unique) txn.createIndex(conn, index)

                // We can have a meta table for transactions, but no history or deleted!
                val meta: PgMeta?
                if (storeMeta) {
                    meta = PgMeta(txn)
                    meta.create(conn)
                    meta.createIndex(conn, PgIndex.rowid_pkey)
                    meta.createIndex(conn, PgIndex.id_unique)
                    for (index in indices) if (index != PgIndex.rowid_pkey && index != PgIndex.id_unique) meta.createIndex(conn, index)
                } else {
                    meta = null
                }
                update(txn, null, null, meta)
                return this
            }
            val head = PgHead(this, storageClass, partitions)
            head.create(conn)
            head.createIndex(conn, PgIndex.rowid_pkey)
            head.createIndex(conn, PgIndex.id_unique)
            for (index in indices) if (index != PgIndex.rowid_pkey && index != PgIndex.id_unique) head.createIndex(conn, index)

            val deleted = if (storedDeleted) PgDeleted(head) else null
            if (deleted != null) {
                deleted.create(conn)
                deleted.createIndex(conn, PgIndex.rowid_pkey)
                deleted.createIndex(conn, PgIndex.id_unique)
                for (index in indices) if (index != PgIndex.rowid_pkey && index != PgIndex.id_unique) deleted.createIndex(conn, index)
            }
            val history = if (storeHistory) PgHistory(head) else null
            if (history != null) {
                history.create(conn)
                history.createYear(conn, NOW.year)
                history.createYear(conn, NOW.year + 1)
                history.createIndex(conn, PgIndex.rowid_pkey)
                history.createIndex(conn, PgIndex.id_txn_uid_unique)
                for (index in indices) if (index != PgIndex.rowid_pkey
                    && index != PgIndex.id_txn_uid_unique
                    // We do not need this index, because it would only duplicate the stronger unique one!
                    && index != PgIndex.id_txn_uid) history.createIndex(conn, index)
            }
            val meta = if (storeMeta) PgMeta(head) else null
            if (meta != null) {
                meta.create(conn)
                meta.createIndex(conn, PgIndex.rowid_pkey)
                meta.createIndex(conn, PgIndex.id_unique)
                for (index in indices) if (index != PgIndex.rowid_pkey && index != PgIndex.id_unique) meta.createIndex(conn, index)
            }
            update(head, deleted, history, meta)
        } finally {
            if (connection == null) {
                conn.commit()
                conn.close()
            }
        }
        return this
    }

    private fun update(head: PgHead?, deleted: PgDeleted?, history: PgHistory?, meta: PgMeta?) {
        this._head = head
        this.deleted = deleted
        this.history = history
        this.meta = meta
        if (head != null) {
            this._exists = true
            this._updateAt = if (PgTable.isInternal(head.name)) Platform.INT64_MAX_VALUE else currentMillis() + HOUR
        } else {
            this._exists = false
            this._updateAt = currentMillis() + SECOND * 20
        }
    }

    /**
     * Add the before and after triggers.
     * @param sql The SQL API.
     * @param id The collection identifier.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     */
    private fun collectionAttachTriggers(sql: PgConnection, id: String, schema: String, schemaOid: Int) {
        var triggerName = id + "_before"
        var rows = sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid))
        if (rows.isRow()) {
            val schemaQuoted = quoteIdent(schema)
            val tableNameQuoted = quoteIdent(id)
            val triggerNameQuoted = quoteIdent(triggerName)
            sql.execute(
                """CREATE TRIGGER $triggerNameQuoted BEFORE INSERT OR UPDATE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_before();"""
            )
        }

        triggerName = id + "_after"
        rows = sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid))
        if (rows.isRow()) {
            val schemaQuoted = quoteIdent(schema)
            val tableNameQuoted = quoteIdent(id)
            val triggerNameQuoted = quoteIdent(triggerName)
            sql.execute(
                """CREATE TRIGGER $triggerNameQuoted AFTER INSERT OR UPDATE OR DELETE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_after();"""
            )
        }
    }

    /**
     * Drop this collection, all tables and indices.
     * @param connection the connection to use to query the database; if _null_, then a new connection is used and auto-committed.
     */
    fun drop(connection: PgConnection? = null) {
        val s = schema.storage
        val conn = connection ?: s.newConnection(s.defaultOptions.copy(schema = schema.name, useMaster = true, readOnly = false))
        try {
            check(!PgTable.isInternal(id)) {
                throw NakshaException(ILLEGAL_ARGUMENT, "It is not allowed to modify internal tables", id = id)
            }
            if (exists(conn)) {
                drop_internal(conn)
            }
        } finally {
            if (connection == null) {
                conn.commit()
                conn.close()
            }
        }
    }

    internal fun drop_internal(conn: PgConnection) {
        var SQL = "DROP TABLE IF EXISTS ${head.quotedName} CASCADE;"
        val history = this.history
        if (history != null) SQL += "DROP TABLE IF EXISTS ${history.quotedName} CASCADE;"
        val deleted = this.deleted
        if (deleted != null) SQL += "DROP TABLE IF EXISTS ${deleted.quotedName} CASCADE;"
        val meta = this.meta
        if (meta != null) SQL += "DROP TABLE IF EXISTS ${meta.quotedName} CASCADE;"
        logger.info("Drop collection {}: {}", id, SQL)
        conn.execute(SQL).close()
    }

    /**
     * Refresh the cached information of this collection.
     * @param connection the connection to query the database; if _null_, a new data connection is acquired, used, and released.
     * @param noCache normally calls to refresh are ignored, when the cache has been updated already, because, except for deletion or creation, collections are not mutable and therefore can be cached basically forever. However, to avoid a cache stale, setting this parameter to _true_ forces an update.
     *
     * @throws NakshaException if any error happened.
     */
    open fun refresh(connection: PgConnection? = null, noCache: Boolean = false): PgCollection {
        if (noCache || _updateAt == null || currentMillis() >= _updateAt) {
            lock.acquire().use {
                // If another thread was faster and updated the values, we ignore noCache
                // This is done. because actually the value was updated instantly before, there is no need to update again!
                val updateAt = _updateAt
                if (updateAt != null && currentMillis() < updateAt) return this
                val s = schema.storage
                val conn = connection ?: s.newConnection(s.defaultOptions.copy(schema = schema.name, useMaster = true, readOnly = false))
                var done: Boolean = false
                var head: PgHead? = null
                var deleted: PgDeleted? = null
                var history: PgHistory? = null
                var meta: PgMeta? = null
                try {
                    val cursor = PgRelation.select(conn, schema.name, id)
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
                            if (id == Naksha.VIRT_TRANSACTIONS) {
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
                                    val txn = PgTransactions(this)
                                    for (entry in historyYears) txn.years[entry.key] = PgTransactionsYear(txn, entry.key)
                                    head = txn
                                } else {
                                    if (parts < 2 || parts > 256) {
                                        throw NakshaException(
                                            ILLEGAL_STATE,
                                            "Invalid amount of HEAD partitions found, must be 2..256, but is ${headPartitions.size}"
                                        )
                                    }
                                    head = PgHead(this, headRelation.storageClass, parts)
                                }
                            } else {
                                head = PgHead(this, headRelation.storageClass, 0)
                            }
                            // TODO: KotlinCompilerBug - No, "head" can't be null!
                            for (index in headIndices) head!!.addIndex(index)
                        }
                        if (historyRelation != null) {
                            if (head == null) throw NakshaException(ILLEGAL_STATE, "Missing HEAD table for collection '$id'")
                            // TODO: KotlinCompilerBug - No, "head" and "history", both can't be null!
                            history = PgHistory(head!!)
                            for (entry in historyYears) history!!.years[entry.key] = PgHistoryYear(history!!, entry.key)
                        }
                        if (deletedRelation != null) {
                            if (head == null) throw NakshaException(ILLEGAL_STATE, "Missing HEAD table for collection '$id'")
                            // TODO: KotlinCompilerBug - No, "head" and "deleted", both can't be null!
                            deleted = PgDeleted(head!!)
                            for (index in deletedIndices) deleted!!.addIndex(index)
                        }
                        if (metaRelation != null) {
                            if (head == null) throw NakshaException(ILLEGAL_STATE, "Missing HEAD table for collection '$id'")
                            // TODO: KotlinCompilerBug - No, "head" and "meta", both can't be null!
                            meta = PgMeta(head!!)
                            for (index in metaIndices) meta!!.addIndex(index)
                        }
                    }
                    // TODO: Updated _nakshaCollection !
                    done = true
                } finally {
                    if (done) update(head, deleted, history, meta) else update(null, null, null, null)
                    if (connection == null) {
                        conn.commit()
                        conn.close()
                    }
                }
            }
        }
        return this
    }

    /**
     * Add the index (does not fail, when the index exists already).
     * @throws NakshaException if the collection does not exist.
     */
    fun addIndex(
        conn: PgConnection,
        index: PgIndex,
        toHead: Boolean = true,
        toDelete: Boolean = true,
        toHistory: Boolean = true,
        toMeta: Boolean = true
    ) {
        TODO("Implement me")
    }

    /**
     * Drop the index (does not fail, when the index does not exist).
     * @throws NakshaException if the collection does not exist.
     */
    fun dropIndex(
        conn: PgConnection,
        index: PgIndex,
        fromHead: Boolean = true,
        fromDelete: Boolean = true,
        fromHistory: Boolean = true,
        fromMeta: Boolean = true
    ) {
        TODO("Implement me")
    }

    // TODO: We can add more helpers, e.g. to calculate statistics, to drop history being to old, ...
    // get_byte(digest('hellox','md5'),0)

}