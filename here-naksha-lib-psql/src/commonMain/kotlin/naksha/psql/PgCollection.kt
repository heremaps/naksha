package naksha.psql

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.NakshaErrorCode.StorageErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaErrorCode.StorageErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.StorageException
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport

/**
 * A collection is a set of database tables, that together form a logical feature store. This lower level implementation ensures that all collection information are cached, including statistical information, and that the cache is refreshed on demand from time to time.
 *
 * Additionally, this implementation supports methods to create new collections (so the whole set of tables), or to refresh the information about the collection, to add or remove indices at runtime.
 *
 * This table should only be used in combination with [naksha.model.NakshaCollectionProxy] and is build according to the data stored in the feature. Actually, clients do operate on the collection features, and the `lib-psql` internally modifies the `PgCollection` accordingly to the external instructions. So, this class is a low level helper, and should only be used with great care, when directly using `lib-psql`.
 *
 * @constructor Creates a new collection object.
 * @property schema the schema.
 * @property id the collection id.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgCollection internal constructor(val schema: PgSchema, val id: String) {
    /**
     * A lock internally used to synchronize access, like cache refresh.
     */
    protected val lock = Platform.newLock()

    /**
     * The epoch milliseconds when to automatically refresh the cache. If _null_, it needs to be refreshed instantly.
     */
    protected var updateAt: Int64? = null

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
     * If this is a `TRANSACTION` table, then the partitioning is done by [naksha.model.Txn.year].
     *
     * Writing directly into partitions, or reading from them, is discouraged, but in some cases necessary to improve performance drastically. In AWS the speed of every [single-flow](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html) connection is limited to 5 Gbps (10 Gbps when being in the same [cluster placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster)), but still always limited. When the PostgresQL database and the client both have higher bandwidth, then multiple parallel connection need to be used, for example to saturate the HERE temporary or consistent store bandwidth of 200 Gbps, between 20 and 40 connections are needed.
     *
     * **Notes**:
     * - The `TRANSACTION` table is not designed for ultra-high throughput, but rather as a storage that allows easy and fast garbage collection and to query ordered by transaction numbers or _sequence numbers_.
     * - Internal tables do not allow to add or remove indices, while ordinary consumer tables do allow this.
     *
     * @throws StorageException if the collection does not exist.
     */
    val head: PgHead
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
            return _head ?: throwStorageException(COLLECTION_NOT_FOUND, id = id)
        }

    /**
     * The history can be disabled fully or temporary. When disabled fully, no history tables are created, in that case this property will be _null_.
     *
     * If the history tables were created, they are always partitioned by the year of `txn_next`. Each yearly table is partitioned again the same way that [HEAD][head] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the history. The history therefore managed the same way as [HEAD][head], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     *
     * @throws StorageException if the collection does not exist.
     */
    var history: PgHistory? = null
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
            return field
        }
        private set

    /**
     * The deletion table can be disabled fully or temporary. When disabled fully, no deletion tables are created, in that case this property will be _null_.
     *
     * If the deletion tables are created, deleted features (not being purged) will be copied into this shadow deletion table. The deletion table is partitioned again the same way that [HEAD][head] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the deletion table. The deletion therefore managed the same way as [HEAD][head], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     *
     * @throws StorageException if the collection does not exist.
     */
    var deleted: PgDeleted? = null
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
            return field
        }
        private set

    /**
     * An optional metadata table, never partitioned. This table is used to as internal storage for metadata, like statistics, calculated by background jobs and other information like this. It can be used as well by applications, and is accessible from outside, but does not have any history or track changes.
     *
     * @throws StorageException if the collection does not exist.
     */
    var meta: PgTable? = null
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
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
     * @return either the existing collection or the created one.
     */
    open fun create(
        connection: PgConnection? = null,
        partitions: Int = 1,
        storageClass: PgStorageClass = PgStorageClass.Consistent,
        storeHistory: Boolean = true,
        storedDeleted: Boolean = true,
        storeMeta: Boolean = true
    ): PgCollection {
        check(!PgTable.isInternal(id)) {
            throwStorageException(ILLEGAL_ARGUMENT, message = "It is not allowed to modify internal tables", id = id)
        }
        PgUtil.ensureValidCollectionId(id)
        check(partitions in 1..256) {
            throwStorageException(ILLEGAL_ARGUMENT, "Invalid amount of partitions requested, must be 0 to 256, was: $partitions")
        }
        return create_internal(connection, partitions, storageClass, storeHistory, storedDeleted, storeMeta)
    }

    internal fun create_internal(
        connection: PgConnection?,
        partitions: Int,
        storageClass: PgStorageClass,
        storeHistory: Boolean,
        storedDeleted: Boolean,
        storeMeta: Boolean
    ) : PgCollection {
        val s = schema.storage
        val conn = connection ?: s.newConnection(s.defaultOptions.copy(schema = schema.name, useMaster = true, readOnly = false))
        try {
            val head = if (this.id == NakshaTransactions.ID) PgTransactions(this) else PgHead(this, storageClass, 32)
            val deleted = if (head !is PgTransactions && storedDeleted) PgDeleted(head) else null
            val history = if (head !is PgTransactions && storeHistory) PgHistory(head) else null
            val meta = if (head !is PgTransactions && storeMeta) PgMeta(head) else null

            head.create(conn)
            deleted?.create(conn)
            history?.create(conn)
            meta?.create(conn)

            this._head = head
            this.deleted = deleted
            this.history = history
            this.meta = meta
            // TODO: Create all indices
        } finally {
            if (connection == null) {
                conn.commit()
                conn.close()
            }
        }
        return this
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
                throwStorageException(ILLEGAL_ARGUMENT, message = "It is not allowed to modify internal tables", id = id)
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
        if (history != null) SQL+= "DROP TABLE IF EXISTS ${history.quotedName} CASCADE;"
        val deleted = this.deleted
        if (deleted != null) SQL+= "DROP TABLE IF EXISTS ${deleted.quotedName} CASCADE;"
        val meta = this.meta
        if (meta != null) SQL+= "DROP TABLE IF EXISTS ${meta.quotedName} CASCADE;"
        logger.info("Drop collection {}: {}", id, SQL)
        conn.execute(SQL).close()
    }

    /**
     * Refresh the cached information of this collection. It is highly recommended to call this function.
     * @param connection the connection to query the database; if _null_, a new data connection is acquired, used, and released.
     * @param noCache normally calls to refresh are ignored, when the cache has been updated in the last few seconds to avoid over usage of the database in case of bad programming, setting this parameter to _true_ forces an update, even when the last update was just done a few milliseconds before; use this parameter with great care!
     *
     * @throws StorageException if any error happened.
     */
    open fun refresh(connection: PgConnection? = null, noCache: Boolean = false): PgCollection {
        if (noCache || updateAt == null || Platform.currentMillis() >= updateAt) {
            lock.acquire().use {
                // Another thread was faster and updated the values.
                // At this point, we ignore noCache, because actually the value was updated instantly before,
                // there is really no need to update again!
                if (updateAt != null || Platform.currentMillis() < updateAt) return this
                TODO("Implement me")
                val quotedSchema = "'foo'"
                val quotedId = "'naksha~collections'"
                val quotedLike = "'naksha~collections$%'"
                val SQL = """
with i as (select oid, nspname from pg_namespace where nspname=$quotedSchema)
select c.oid as table_oid, c.relname as table_name, i.oid as schema_oid, i.nspname as schema, c.relkind, c.relpersistence sc
from pg_class c, i
where (c.relkind='r' or c.relkind='p') and c.relnamespace = i.oid
and (c.relname=$quotedId or c.relname like $quotedLike)
order by table_name;
"""
            }
        }
        return this
    }

    private var _indices: List<PgCollectionIndex>? = null

    /**
     * Returns a list of all collection indices.
     * @throws StorageException if the collection does not exist.
     */
    val indices: List<PgCollectionIndex>
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
            return _indices ?: throwStorageException(COLLECTION_NOT_FOUND, id = id)
        }

    /**
     * Add the index (does not fail, when the index exists already).
     * @throws StorageException if the collection does not exist.
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
     * @throws StorageException if the collection does not exist.
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