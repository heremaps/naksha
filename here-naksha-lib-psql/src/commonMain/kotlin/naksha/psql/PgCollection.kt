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
     * The columns present in the HEAD (and META/DELETED) tables of this collection.
     *
     * Always includes the full set of default head columns. If the collection declares additional
     * members that map to known [PgColumn] entries (e.g. `pn`, `pt`, `gv`) those columns are
     * appended so that every physically-present column is covered in a single list.
     * @since 3.0
     */
    val effectiveHeadColumns: List<PgColumn>
        get() {
            val members = head.members ?: return PgColumn.headColumns
            val byName = PgColumn.allColumnsByName
            val base = PgColumn.headColumns
            val extras = mutableListOf<PgColumn>()
            for (m in members) {
                if (m == null) continue
                val col = byName[m.name]
                if (col != null && col !in base && col !in extras) extras.add(col)
            }
            return if (extras.isEmpty()) base else base + extras
        }

    /**
     * The columns present in the HISTORY tables of this collection.
     *
     * Mirrors [effectiveHeadColumns] but includes [PgColumn.next_version] for HISTORY.
     * @since 3.0
     */
    val effectiveHistoryColumns: List<PgColumn>
        get() {
            val members = head.members ?: return PgColumn.allColumns
            val byName = PgColumn.allColumnsByName
            val base = PgColumn.allColumns
            val extras = mutableListOf<PgColumn>()
            for (m in members) {
                if (m == null) continue
                val col = byName[m.name]
                if (col != null && col !in base && col !in extras) extras.add(col)
            }
            return if (extras.isEmpty()) base else base + extras
        }

    /**
     * The subset of [PgColumn.copyIntoHistoryColumns] that actually exist in this collection's tables.
     * Used when copying HEAD rows into HISTORY to avoid referencing absent optional columns.
     * @since 3.0
     */
    val effectiveCopyIntoHistoryColumns: List<PgColumn>
        get() {
            val effective = effectiveHeadColumns.toSet()
            return PgColumn.copyIntoHistoryColumns.filter { it in effective }
        }

    /**
     * The subset of [PgColumn.updateColumns] that actually exist in this collection's tables.
     * Used in UPDATE CTEs to avoid referencing absent optional columns.
     * @since 3.0
     */
    val effectiveUpdateColumns: List<PgColumn>
        get() {
            val effective = effectiveHeadColumns.toSet()
            return PgColumn.updateColumns.filter { it in effective }
        }

    /**
     * The amount of performance partitions.
     */
    val partitions: Int
        get() = head.partitions

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
        val partitions = if (nakshaCollection.partitions == 1) 0 else nakshaCollection.partitions
        storageClass = PgStorageClass.of(nakshaCollection.storageClass)
        @Suppress("LeakingThis")
        headTable = PgHead(this, storageClass, partitions)
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
    @Deprecated(message = "Needs to be fixed before used", level = DeprecationLevel.ERROR)
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
     * Diff the [prev] and [next] collection definitions and apply the resulting schema changes (ADD/DROP COLUMN, CREATE/DROP custom index) to all variant tables (HEAD, HISTORY, DELETED, META).
     *
     * Members:
     * - Same name + same `dataType` → no-op.
     * - Same name + different `dataType` → throws [NakshaError.ILLEGAL_ARGUMENT] (type changes are not supported).
     * - Added → `ALTER TABLE ADD COLUMN` on each root variant.
     * - Removed → requires [force] = `true`; otherwise throws [NakshaError.ILLEGAL_ARGUMENT]. With `force`, emits `ALTER TABLE DROP COLUMN` on each root variant.
     *
     * Custom indexes:
     * - Identity is `(name, type, on, include, unique)`. Any difference → drop + create.
     *
     * Only root tables are touched; partition tables inherit `ADD/DROP COLUMN` via Postgres declarative partitioning.
     *
     * @since 3.0
     */
    internal fun applyMembersAndIndexes(
        conn: PgConnection,
        prev: NakshaCollection,
        next: NakshaCollection,
        force: Boolean
    ) {
        diffMembers(conn, prev, next, force)
        diffIndexes(conn, prev, next)
    }

    private fun diffMembers(conn: PgConnection, prev: NakshaCollection, next: NakshaCollection, force: Boolean) {
        val prevMembers = prev.members
        val nextMembers = next.members
        val prevByName = mutableMapOf<String, naksha.model.objects.Member>()
        val nextByName = mutableMapOf<String, naksha.model.objects.Member>()
        // Mandatory columns are always present in the DB and never appear in members lists,
        // so we skip them here entirely.
        val mandatoryNames = PgColumn.mandatoryMembers.map { it.name }.toSet()
        if (prevMembers != null) for (m in prevMembers) if (m != null && m.name !in mandatoryNames) prevByName[m.name] = m
        if (nextMembers != null) for (m in nextMembers) if (m != null && m.name !in mandatoryNames) nextByName[m.name] = m

        // Type-change check.
        for ((name, nm) in nextByName) {
            val pm = prevByName[name] ?: continue
            if (pm.dataType != nm.dataType) {
                throw naksha.model.illegalArg(
                    "Change of dataType for member '$name' is not supported (was ${pm.dataType}, requested ${nm.dataType})"
                )
            }
        }

        // Added.
        for ((name, nm) in nextByName) {
            if (prevByName.containsKey(name)) continue
            val pgIdent = "\"${PgCustomMemberValues.pgColumnName(name)}\""
            val pgType = PgCustomMemberValues.pgSqlTypeFor(nm.dataType)
            for (root in mutableRootTables()) {
                val sql = "ALTER TABLE ${root.quotedName} ADD COLUMN IF NOT EXISTS $pgIdent $pgType"
                conn.execute(sql).close()
            }
        }

        // Removed.
        for ((name, _) in prevByName) {
            if (nextByName.containsKey(name)) continue
            if (!force) {
                throw naksha.model.illegalArg(
                    "Member '$name' is being removed from collection '$id', but Write.force is false. " +
                        "Set Write.force = true to allow ALTER TABLE DROP COLUMN."
                )
            }
            val pgIdent = "\"${PgCustomMemberValues.pgColumnName(name)}\""
            for (root in mutableRootTables()) {
                val sql = "ALTER TABLE ${root.quotedName} DROP COLUMN IF EXISTS $pgIdent"
                conn.execute(sql).close()
            }
        }
    }

    private fun diffIndexes(conn: PgConnection, prev: NakshaCollection, next: NakshaCollection) {
        val prevIdx = prev.indices
        val nextIdx = next.indices
        val prevByName = mutableMapOf<String, naksha.model.objects.Index>()
        val nextByName = mutableMapOf<String, naksha.model.objects.Index>()
        // Internal (mandatory) indices are always managed by the storage; skip them in the diff.
        if (prevIdx != null) for (ci in prevIdx) if (ci != null && !ci.isInternal()) prevByName[ci.name] = ci
        if (nextIdx != null) for (ci in nextIdx) if (ci != null && !ci.isInternal()) nextByName[ci.name] = ci

        // Removed (or changed): drop on every root.
        for ((name, pi) in prevByName) {
            val ni = nextByName[name]
            if (ni == null || !customIndexEquals(pi, ni)) {
                for (root in mutableRootTables()) root.dropCustomIndex(conn, name)
            }
        }

        // Added (or changed): create on every root.
        for ((name, ni) in nextByName) {
            val pi = prevByName[name]
            if (pi == null || !customIndexEquals(pi, ni)) {
                for (root in mutableRootTables()) root.createCustomIndex(conn, ni)
            }
        }
    }

    private fun customIndexEquals(a: naksha.model.objects.Index, b: naksha.model.objects.Index): Boolean {
        if (a.name != b.name) return false
        if (a.type != b.type) return false
        if (a.isUnique() != b.isUnique()) return false
        if (!listsEqual(a.on, b.on)) return false
        if (!listsEqual(a.include, b.include)) return false
        return true
    }

    private fun listsEqual(a: naksha.base.StringList?, b: naksha.base.StringList?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return a?.isEmpty() ?: b?.isEmpty() ?: true
        if (a.size != b.size) return false
        for (i in 0 until a.size) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    private fun mutableRootTables(): List<PgTable> {
        val result = mutableListOf<PgTable>()
        result.add(headTable)
        historyTable?.let { result.add(it) }
        metaTable?.let { result.add(it) }
        return result
    }
}