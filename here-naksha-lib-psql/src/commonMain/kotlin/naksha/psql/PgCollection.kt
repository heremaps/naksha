package naksha.psql

import naksha.base.*
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A collection is a set of database tables, that together form a logical feature store. This lower level implementation supports methods to create the collection physically (so the whole set of tables), to refresh the information about the collection, drop the tables, to add, or remove indices at runtime, aso.
 *
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgCollection internal constructor(
    /**
     * The map in which the collection is located.
     * @since 3.0
     */
    val map: PgMap,

    /**
     * The HEAD state of the collection.
     * @since 3.0
     */
    nakshaCollection: NakshaCollection,

    /**
     * The map-id.
     * @since 3.0
     */
    val id: String = nakshaCollection.id,

    /**
     * The map-number.
     * @since 3.0
     */
    val number: Int = nakshaCollection.number
) {
    /**
     * The weak-reference to this [PgCollection], should be used when the collection should be cached.
     * @since 3.0
     */
    @Suppress("LeakingThis")
    val weakRef = WeakRef(this)
    
    /**
     * The storage in which the collection is located.
     * @since 3.0
     */
    val storage: PgStorage
        get() = map.storage

    /**
     * The _HEAD_ state of the collection.
     *
     * ### Note
     * If the collection is deleted, this value stays unmodified, because the [PgCollection] will be removed from caching. However, if only the _HEAD_ state of the collection is modified, so basically an `UPDATE` is done, the _HEAD_ reference is replaced on-the-fly.
     * @since 3.0
     */
    val headRef = AtomicNonNullRef(nakshaCollection)

    /**
     * Reads [headRef].
     * @see [headRef]
     * @since 3.0
     */
    val head: NakshaCollection
        get() = headRef.get()

    /**
     * The storage class of the collection.
     */
    var storageClass: PgStorageClass = PgStorageClass.Unknown
        internal set

    /**
     * The amount of performance partitions.
     */
    var partitions: Int = 1
        internal set

    /**
     * The `HEAD` table, so where to store features or transactions into.
     *
     * If this is an ordinary table, that can be partitioned using [PgPlatform.partitionNumber] above the [PgColumn.id], except when this is a `TRANSACTION` collection, then the partitioning is done by [Version.year], extracted from [PgColumn.txn].
     *
     * Writing directly into partitions, or reading from them, is discouraged, but in some cases necessary to improve performance drastically. In AWS the speed of every [single-flow](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html) connection is limited to 5 Gbps (10 Gbps when being in the same [cluster placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster)), but still always limited. When the PostgresQL database and the client both have higher bandwidth, then multiple parallel connection need to be used, for example to saturate the HERE temporary or consistent store bandwidth of 200 Gbps, between 20 and 40 connections are needed.
     *
     * **Notes**:
     * - The `TRANSACTION` table is not designed for ultra-high throughput, but rather as a storage that allows easy and fast garbage collection and to query ordered by transaction numbers or _sequence numbers_.
     * - Internal tables do not allow to add or remove indices, while ordinary consumer tables do allow this.
     */
    var headTable: PgHead
        internal set

    /**
     * The history can be disabled fully or temporary. When disabled fully, no history tables are created, in that case this property will be _null_.
     *
     * If the history tables were created, they are always partitioned by the year of `txn_next`. Each yearly table is partitioned again the same way that [HEAD][headTable] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the history. The history therefore managed the same way as [HEAD][headTable], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     */
    var historyTable: PgHistory?
        internal set

    /**
     * The deletion table can be disabled fully or temporary. When disabled fully, no deletion tables are created, in that case this property will be _null_.
     *
     * If the deletion tables are created, deleted features (not being purged) will be copied into this shadow deletion table. The deletion table is partitioned again the same way that [HEAD][headTable] is partitioned, not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the deletion table. The deletion therefore managed the same way as [HEAD][headTable], so using the [PgPlatform.partitionNumber] above the `feature.id`.
     */
    var deletedTable: PgDeleted?
        internal set

    /**
     * An optional metadata table, never partitioned. This table is used to as internal storage for metadata, like statistics, calculated by background jobs and other information like this. It can be used as well by applications, and is accessible from outside, but does not have any history or track changes.
     */
    var metaTable: PgTable?
        internal set

    /**
     * Tests if this is an internal collection. Internal collections have some limitation, for example it is not possible to add or drop indices, nor can they be created through the normal [create] method. They are basically immutable by design, but the content can be read and modified to some degree.
     *
     * **Warning**: Internal tables may have further limitations, even about the content, for example the transaction log only allows to mutate `tags` for normal external clients. Internal clients may perform other mutations, e.g. the internal _sequencer_ is allowed to set the `seqNumber`, `seqTs`, `geo` and `geo_ref` columns, additionally it will update the feature counts, when necessary. However, for normal clients the transaction log is immutable, and the _sequencer_ will only alter the transactions ones in their lifetime.
     */
    val internal: Boolean
        get() = id.startsWith("naksha~")

    init {
        storageClass = PgStorageClass.of(nakshaCollection.storageClass)
        @Suppress("LeakingThis")
        headTable = if (this is PgNakshaTransactions) PgTransactions(this) else PgHead(this, storageClass, nakshaCollection.partitions)
        deletedTable = if (nakshaCollection.storeDeleted == StoreMode.OFF) null else PgDeleted(headTable)
        historyTable = if (nakshaCollection.storeHistory == StoreMode.OFF) null else PgHistory(headTable)
        metaTable = if (nakshaCollection.storeMeta == StoreMode.OFF) null else PgMeta(headTable)
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
}