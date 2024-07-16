package naksha.psql

import naksha.model.NakshaCollectionProxy
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A Naksha collection is a set of database tables, that together form a logical data storage. This lower level implementation
 * ensures that all collection information are cached, including statistical information, and that the cache is refreshed on demand from
 * time to time.
 *
 * Additionally, this implementation supports methods to create new collections (so the whole set of tables) or to refresh the
 * information about the collection.
 *
 * @constructor Creates a new collection object.
 * @property id the collection id (prefix).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgCollection private constructor(val id: String) {

    companion object PgCollectionCompanion {
        /**
         * Get or create a collection.
         * @param conn the connection to use to query the database.
         * @param id the identifier of the collection.
         * @param partitions the number of partitions to create, must be a value between 1 and 256.
         * @param storageClass the storage class to use.
         * @param history if the history should be stored, creating the corresponding history partitions.
         * @param deleted if deleted features should be stored, creating the corresponding deletion partitions.
         * @param meta if the meta-table should be created, which stores internal meta-data. Without this table, no statistics can be
         * acquired and collected.
         * @param indices the indices to create. These indices will be created on all tables by default, if more individual control is
         * wished, leave the indices array empty and created the indices individually.
         * @return either the existing collection or the created one.
         */
        @JsStatic
        @JvmStatic
        fun createOrGet(
            conn: PgConnection,
            id: String,
            partitions: Int = 1,
            storageClass: PgStorageClass = PgStorageClass.Consistent,
            history: Boolean = true,
            deleted: Boolean = true,
            meta: Boolean = true,
            indices: Array<PgIndex> = arrayOf(PgIndex.id, PgIndex.txn_uid)
        ): PgCollection {
            TODO("Implement me!")
            while (true) {
                val collection = get(conn, id)
                if (collection != null) return collection
                try {
                } catch (e: Exception) {

                }
            }
        }

        /**
         * Returns the [PgCollection] with the given identifier or _null_, if no such collection exists.
         * @param conn the connection to use to query the database.
         * @param id the collection identifier.
         * @return the [PgCollection] with the given identifier or _null_, if no such collection exists.
         */
        @JsStatic
        @JvmStatic
        fun get(conn: PgConnection, id: String): PgCollection? {
            TODO("Implement me")
        }

        private fun _create(
            conn: PgConnection,
            id: String,
            partitions: Int,
            storageClass: PgStorageClass,
            history: Boolean,
            deleted: Boolean,
            meta: Boolean,
            indices: Array<PgIndex>
        ): PgCollection {
            val info = conn.info()
            val (CREATE_TABLE, TABLESPACE) = when (storageClass) {
                PgStorageClass.Brittle -> Pair(
                    "CREATE UNLOGGED TABLE",
                    if (info.brittleTableSpace != null) "TABLESPACE ${info.brittleTableSpace}" else ""
                )

                PgStorageClass.Temporary -> Pair(
                    "CREATE UNLOGGED TABLE",
                    if (info.tempTableSpace != null) "TABLESPACE ${info.tempTableSpace}" else ""
                )

                else -> Pair("CREATE TABLE", "")
            }
            // See: https://www.postgresql.org/docs/current/storage-toast.html
            // - PLAIN prevents either compression or out-of-line storage.
            // - EXTENDED allows both compression and out-of-line storage.
            // - EXTERNAL allows out-of-line storage but not compression.
            // - MAIN allows compression but not out-of-line storage.
            //   (Actually, out-of-line storage will still be performed for such columns, but only as a
            //   last resort when there is no other way to make the row small enough to fit on a page.)
            //
            // The TOAST code will compress and/or move field values out-of-line until
            // the row value is shorter than TOAST_TUPLE_TARGET bytes.
            // Note: We order the columns by intention like this to minimize the storage cost.
            //       The bytea columns will always be GZIP compressed (see flags).
            val TABLE_BODY = """(
    created_at   int8,
    updated_at   int8 NOT NULL,
    author_ts    int8,
    txn_next     int8,
    txn          int8 NOT NULL,
    ptxn         int8,
    uid          int4,
    puid         int4,
    hash         int4,
    change_count int4,
    geo_grid     int4,
    flags        int4,
    id           text STORAGE PLAIN NOT NULL COLLATE "C.UTF8" PRIMARY KEY,
    origin       text STORAGE PLAIN COLLATE "C.UTF8",
    app_id       text STORAGE PLAIN NOT NULL COLLATE "C.UTF8",
    author       text STORAGE PLAIN COLLATE "C.UTF8",
    type         text STORAGE PLAIN COLLATE "C.UTF8",
    tags         bytea STORAGE PLAIN,
    geo_ref      bytea STORAGE PLAIN,
    geo          bytea STORAGE EXTERNAL,
    feature      bytea STORAGE EXTERNAL
)"""
            val WITH_IMMUTABLE = "(fillfactor=100,toast_tuple_target=${info.maxTupleSize},parallel_workers=${partitions})"
            val WITH_VOLATILE = "(fillfactor=65,toast_tuple_target=${info.maxTupleSize},parallel_workers=${partitions})"
            // Syntax: $CREATE_TABLE name $TABLE_BODY $PARTITION $WITH $TABLESPACE
            // PARTITION BY { RANGE | LIST | HASH } ( { column_name | ( expression ) } [ COLLATE collation ] [ opclass ] [, ... ] ) ]
            TODO("Finish me!")
        }
    }

    /**
     * The HEAD table where to insert features into.
     */
    var headTable: Array<PgTable>? = null
        private set

    /**
     * If the HEAD table is a partitioned table, so [PgTable.kind] is [PgKind.PartitionTable], then this array holds the partitions in
     * order by their partition number.
     */
    var headPartitions: Array<PgTable>? = null
        private set

    /**
     * If the HEAD table is a partitioned table, so [PgTable.kind] is [PgKind.PartitionTable], and this is the transaction table, then
     * this array holds the transaction partitions. This is a special hack that only applies to the transaction table, where the
     * transactions are partitioned by transaction number (`txn`), rather than by `id`. The key is the month identifier in the format
     * `yyyy_mm`, so the same way the history is managed.
     */
    var txPartitions: Map<String, PgTable>? = null
        private set

    /**
     * The history table, must be a partitioned table, except no history was created for this collection.
     */
    var historyTable: PgTable? = null
        private set

    /**
     * The history partitions. The history table is always partitioned by month, therefore the key is a string in the format `yyyy_mm`,
     * for example `2024_01`. If the HEAD partition is partitioned, then the monthly history is as well partitioned, the same way as the
     * HEAD table is partitioned. If not, the inner array has always the size of 1. The inner tables are ordered by partition number.
     */
    var historyPartitions: Map<String, Array<PgTable>>? = null
        private set

    /**
     * The deletion table.
     */
    var deleteTable: PgTable? = null
        private set

    /**
     * If the HEAD table is partitioned, and the [deletion table][deleteTable] exists, it is expected to be as well a partitioned table
     * and this holds the partitions, again ordered by partition number.
     */
    var deletePartitions: Array<PgTable>? = null
        private set

    /**
     * An optional meta data table.
     */
    var meta: PgTable? = null
        private set

    /**
     * Tests if this is an internal collection. Internal collections have some limitation, for example it is not possible to add or drop
     * indices, not can they be created through the normal [createOrGet] method. They are basically immutable by design, but the content
     * can be read and modified.
     *
     * **Warning**: Internal tables may have further limitations even about the content, for example the transaction log only allows to
     * mutate `tags` for normal external clients. Internal clients may perform other mutations, e.g. the internal _sequencer_ is allowed to
     * set the `seqNumber`, `seqTs`, `geo` and `geo_ref` columns, additionally it will update the feature counts, when necessary. However,
     * for normal clients that transaction log is immutable, and the _sequencer_ will only alter the transaction ones in its lifetime.
     */
    val internal: Boolean
        get() = id.startsWith("naksha~")

    /**
     * Create a Naksha feature from the information of this collection. Beware, that the collection feature is only partially filled, but as
     * internal collection are not stored in the collection management collection (because the management collection itself is created
     * using a [PgCollection]), they are immutable, and will not contain all information.
     */
    fun copyToNakshaFeature(): NakshaCollectionProxy {
        TODO("PgCollection::copyToNakshaFeature is not yet implemented")
    }

    fun refresh(): PgCollection {
        TODO("Implement me")
    }

    /**
     * Returns a list of all collection indices.
     */
    fun getIndices(): Array<PgCollectionIndex> {
        TODO("Implement me")
    }

    /**
     * Add the index (does not fail, when the index exists already).
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
}