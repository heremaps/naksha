@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.jbon.NakshaTxn.Companion.SEQ_MIN
import com.here.naksha.lib.jbon.NakshaTxn.Companion.SEQ_NEXT
import kotlinx.datetime.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A session linked to a PostgresQL database connection with support for some special table layout and triggers. Its purpose is
 * to support the Naksha `lib-psql`. This implements the code that normally resides inside of Postgres, however, technically
 * it can be run as well in a JVM, but in this case the JVM needs to back the database connection with a JDBC connection.
 *
 * Therefore, in the JVM the code runs the same way it runs inside the PostgresQL database PLV8 extension, but with higher
 * latencies and with triggers being simulated.
 *
 * @property sql Access to database. In PostgresQL bound by the SQL function `naksha_start_session` to the PLV8
 * database connection wrapper. In the JVM bound by the `startSession` call of the `Plv8Env` class.
 * Technically, the `lib-psql` will always use the SQL function, therefore the only reason to use the
 * JVM function `startSession` is, when testing the code to simulate a PLV8 environment.
 * @property schema The database schema of this session.
 * @property storageId The storage-identifier of this session.
 * @param appName The name of the application starting the session, only for debugging purpose.
 * @param streamId The stream-identifier, to be added to the transaction logs for debugging purpose.
 * @param appId The UPM identifier of the application (for audit).
 * @param author The UPM identifier of the user (for audit).
 * @constructor Create a new session.
 */
@Suppress("UNUSED_PARAMETER", "unused", "LocalVariableName", "MemberVisibilityCanBePrivate", "DuplicatedCode")
@JsExport
class NakshaSession(
        val sql: IPlv8Sql,
        val schema: String,
        val storageId: String,
        appName: String, streamId: String, appId: String, author: String? = null
) : JbSession(appName, streamId, appId, author) {
    /**
     * The [object identifier](https://www.postgresql.org/docs/current/datatype-oid.html) of the schema.
     */
    val schemaOid: Int

    /**
     * The [object identifier](https://www.postgresql.org/docs/current/datatype-oid.html) of the transaction sequence.
     */
    val txnSeqOid: Int

    init {
        val quotedSchema = sql.quoteIdent(schema)
        val initQuery = """SET SESSION search_path TO $quotedSchema, public, topology;
SET SESSION enable_seqscan = OFF;
"""
        sql.execute(initQuery)
        schemaOid = asMap(asArray(sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))[0])["oid"]!!
        txnSeqOid = asMap(asArray(sql.execute("SELECT oid FROM pg_class WHERE relname = 'naksha_txn_seq' and relnamespace = $1", arrayOf(schemaOid)))[0])["oid"]!!
    }

    companion object {
        /**
         * Returns the current thread local [NakshaSession].
         * @return The current thread local [NakshaSession].
         * @throws IllegalStateException If the current session is no Naksha session.
         */
        @JvmStatic
        fun get(): NakshaSession = threadLocal.get() as NakshaSession
    }

    /**
     * The current transaction number.
     */
    private var _txn: NakshaTxn? = null

    /**
     * The epoch milliseconds of when the transaction started (`transaction_timestamp()`).
     */
    private var _txts: BigInt64? = null

    /**
     * The PostgresQL transaction number.
     */
    private var _xactId: BigInt64? = null

    /**
     * The last transaction for which the history partition cache is updated.
     */
    private var historyPartitionCacheTxn: NakshaTxn? = null

    /**
     * A cache to remember which history partitions have been verified already. The key is the collection id
     * and the value is just _true_.
     */
    private lateinit var historyPartitionCache: IMap

    /**
     * The cache for the object ids of the collection sequence counters.
     */
    private lateinit var collectionSeqOidCache: IMap

    /**
     * The cache for UID.
     */
    private lateinit var collectionUidCache: IMap

    /**
     * The last error number as SQLState.
     */
    var errNo: String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg: String? = null

    override fun reset(appName: String, streamId: String, appId: String, author: String?) {
        super.reset(appName, streamId, appId, author)
        this._txn = null
        this._txts = null
        this._xactId = null
        this.errNo = null
        this.errMsg = null
    }


    /**
     * Internally invoked by the triggers before writing into history to ensure that the history partition exists.
     * @param id The collection identifier.
     */
    fun ensureHistoryPartition(id: String) {
        // Query current transaction.
        val txn = txn()
        if (!historyPartitionCache.containsKey(id)) {
            // TODO: Create the corresponding history partition (txn_next should match txn())
        }
    }

    /**
     * Fills the internal UID cache for the given collection with the given amount of UIDs.
     * @param collectionId The collection identifier.
     * @param min The minimal amount of UIDs to cache (if that many are still cached, we are okay).
     * @param max The maximum amount of UIDs to cache (if some need to be fetched, then this many are fetched).
     */
    fun prefetchUids(collectionId: String, min: Int, max: Int) {
        require(min in 1..max)
        if (!this::collectionUidCache.isInitialized) collectionUidCache = Jb.map.newMap()
        var cached_uids: ArrayList<BigInt64>? = collectionUidCache[collectionId]
        if (cached_uids == null) {
            cached_uids = ArrayList()
            collectionUidCache[collectionId] = cached_uids
        }
        if (cached_uids.size >= min) return

        // Fill the cache.
        if (!this::collectionSeqOidCache.isInitialized) collectionSeqOidCache = Jb.map.newMap()
        var uidSeqOid: Int? = collectionSeqOidCache[collectionId]
        if (uidSeqOid == null) {
            val seqName = collectionId + "_uid_seq"
            uidSeqOid = asMap(asArray(sql.execute("SELECT oid FROM pg_class WHERE relname = $1 and relnamespace = $2",
                    arrayOf(seqName, schemaOid)))[0])["oid"]!!
            collectionSeqOidCache[collectionId] = uidSeqOid
        }

        val need = max - cached_uids.size
        val rows = asArray(sql.execute("SELECT nextval($1) as v from generate_series(1,$need);", arrayOf(uidSeqOid)))
        var i = 0
        while (i < rows.size) {
            cached_uids.add(asMap(rows[i])["v"]!!)
            i++
        }
    }

    /**
     * Returns a new UID for the given collection from the cached UIDs.
     * @return The new UID.
     */
    fun newUid(collectionId: String): BigInt64 {
        prefetchUids(collectionId, 1, 10)
        val cached_uids: ArrayList<BigInt64> = collectionUidCache[collectionId]!!
        return cached_uids.removeFirst()
    }

    private val xyzBuilder = XyzBuilder(Jb.env.newDataView(ByteArray(500)))

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param featureId The ID of the feature.
     * @param uid The UID to use.
     * @param geoType The geometry-type.
     * @param geo The geometry of the feature as EWKB; if any.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, featureId: String, uid: BigInt64, geoType: Short, geo: ByteArray?): ByteArray {
        val env = Jb.env
        val createdAt = env.currentMillis()
        val txn = txn();
        val extent = Static.extent(sql, geo)
        val uuid = txn.newFeatureUuid(storageId, collectionId, uid).toString()
        val grid = Static.grid(sql, featureId, geoType, geo)
        return xyzBuilder.buildXyzNs(createdAt, createdAt, txn.value, XYZ_OP_CREATE, 1, createdAt, extent, null, uuid, appId, author
                ?: appId, grid)
    }

    private fun xyzUpdate(): ByteArray {
        TODO("Implement me!")
    }

    private fun xyzDelete(): ByteArray {
        TODO("Implement me!")
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [PgTrigger.NEW].
     */
    fun triggerBefore(data: PgTrigger) {
        var collectionId = data.TG_TABLE_NAME
        // Note: "topology_p000" is a partition, but we need collection-id.
        //        0123456789012
        // So, in that case we will find an underscore at index 8, so i = length-4!
        val i = collectionId.lastIndexOf('_')
        if (i >= 0 && i == (collectionId.length - 4) && collectionId[i + 1] == 'p') {
            collectionId = collectionId.substring(0, i)
        }
        if (data.TG_OP == TG_OP_INSERT) {
            check(data.NEW != null) { "Missing NEW for INSERT" }
            data.NEW[COL_TXN_NEXT] = Jb.int64.ZERO()
            val uid = newUid(collectionId)
            data.NEW[COL_UID] = uid
            val id: String? = data.NEW[COL_ID]
            check(id != null) { "Missing id" }
            var geoType: Short? = data.NEW[COL_GEO_TYPE]
            if (geoType == null) {
                geoType = GEO_TYPE_NULL
                data.NEW[COL_GEO_TYPE] = GEO_TYPE_NULL
            }
            data.NEW[COL_XYZ] = xyzInsert(collectionId, id, uid, geoType, data.NEW[COL_GEOMETRY])
        } else if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            TODO("Implement me!")
        }
        // We should not be called for delete, in that case do nothing.
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [PgTrigger.NEW].
     */
    fun triggerAfter(data: PgTrigger) {
        //Jb.log.info("Trigger after")
    }

    /**
     * An internal view to calculate the partition id.
     */
    private lateinit var partView: IDataView

    /**
     * Returns the partition from date.
     * @param year The year, e.g. 2024.
     * @param month The month between 1 (January) and 12 (December).
     * @param day The day between 1 and 31.
     * @return The name of the corresponding history partition.
     */
    fun partitionNameForDate(year: Int, month: Int, day: Int): String {
        val sb = StringBuilder()
        sb.append(year)
        sb.append('_')
        if (month < 10) sb.append('0')
        sb.append(month)
        sb.append('_')
        if (day < 10) sb.append('0')
        sb.append(day)
        return sb.toString() // 2024_01_15
    }

    /**
     * Returns the partition id based upon the given timestamp (for history partitions).
     * @param millis The epoch-timestamp in milliseconds.
     * @return The name of the corresponding history partition.
     */
    fun partitionNameForMillis(millis: BigInt64): String {
        val ts = JbTimestamp.fromMillis(millis)
        return partitionNameForDate(ts.year, ts.month, ts.day)
    }

    /**
     * The cached Postgres version.
     */
    private lateinit var pgVersion: XyzVersion

    /**
     * Returns the PostgresQL version.
     * @return The PostgresQL version.
     */
    fun postgresVersion(): XyzVersion {
        if (!this::pgVersion.isInitialized) {
            val r = sql.execute("select version() as version")
            val rows = sql.rows(r)
            check(rows != null)
            val row = asMap(rows[0])
            // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
            val versionString: String = row["version"]!!
            val firstSpace = versionString.indexOf(' ')
            check(firstSpace > 0)
            val secondSpace = versionString.indexOf(' ', firstSpace + 1)
            check(secondSpace > firstSpace)
            val pgv = versionString.substring(firstSpace + 1, secondSpace)
            pgVersion = XyzVersion.fromString(pgv)
        }
        return pgVersion
    }

    /**
     * Returns the current transaction number, if no transaction number is yet generated, generates a new one.
     * @return The current transaction number.
     */
    fun txn(): NakshaTxn {
        if (_txn == null) {
            val row = asMap(asArray(sql.execute("SELECT txid_current() xactid, nextval($1) txn, (extract(epoch from transaction_timestamp())*1000)::int8 as time", arrayOf(txnSeqOid)))[0])
            _xactId = asBigInt64(row["xactid"])
            val txts = asBigInt64(row["time"])
            _txts = txts
            var txn = NakshaTxn(asBigInt64(row["txn"]))
            _txn = txn
            val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
            val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
            if (txn.year != txDate.year || txn.month != txDate.monthNumber || txn.day != txDate.dayOfMonth) {
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(Static.TXN_LOCK_ID))
                try {
                    val raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnSeqOid)) as Array<Any>)[0])["v"])
                    txn = NakshaTxn(raw)
                    _txn = txn
                    if (txn.year != txDate.year || txn.month != txDate.monthNumber || txn.day != txDate.dayOfMonth) {
                        // Rollover, we update sequence of the day.
                        txn = NakshaTxn.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, BigInt64(0))
                        _txn = txn
                        sql.execute("SELECT setval($1, $2)", arrayOf(txnSeqOid, txn.value + 1))
                    }
                } finally {
                    sql.execute("SELECT pg_advisory_unlock($1)", arrayOf(Static.TXN_LOCK_ID))
                }
            }
            // If the history partition cache does not exist or is outdated, initialize empty.
            val hst_txn = historyPartitionCacheTxn
            if (hst_txn == null || hst_txn.year != txn.year || hst_txn.month != txn.month || hst_txn.day != txn.day) {
                historyPartitionCacheTxn = txn
                historyPartitionCache = Jb.map.newMap()
            }
            val tableName = "naksha_txn_${txn.historyPostfix()}"
            if (!Static.tableExists(sql, tableName, schemaOid)) {
                val start = NakshaTxn.of(txn.year, txn.month, txn.day, SEQ_MIN)
                val end = NakshaTxn.of(txn.year, txn.month, txn.day, SEQ_NEXT)
                val query = """CREATE TABLE $tableName PARTITION OF naksha_txn FOR VALUES FROM (${start.value}) TO (${end.value});
ALTER TABLE $tableName
ALTER COLUMN details SET STORAGE MAIN,
ALTER COLUMN attachment SET STORAGE MAIN,
SET (toast_tuple_target=8160,fillfactor=100
-- Specifies the minimum number of updated or deleted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_threshold=10000,toast.autovacuum_vacuum_threshold=10000
-- Specifies the number of inserted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_insert_threshold=10000,toast.autovacuum_vacuum_insert_threshold=10000
-- Specifies a fraction of the table size to add to autovacuum_vacuum_threshold when deciding whether to trigger a VACUUM.
,autovacuum_vacuum_scale_factor=0.1,toast.autovacuum_vacuum_scale_factor=0.1
-- Specifies a fraction of the table size to add to autovacuum_analyze_threshold when deciding whether to trigger an ANALYZE.
,autovacuum_analyze_threshold=10000,autovacuum_analyze_scale_factor=0.1
);"""
                sql.execute(query)
            }
        }
        return _txn!!
    }

    /**
     * Returns the PostgresQL transaction number ([xact_id](https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-PG-SNAPSHOT).
     * If no transaction number is yet generated, generates a new one.
     * @return The current PostgresQL transaction number.
     */
    fun xactId(): BigInt64 {
        txn()
        return _xactId!!
    }

    fun writeFeatures(
            collectionId: String,
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>
    ): ITable {
        errNo = null
        errMsg = null
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        val table = sql.newTable()
        val opReader = XyzOp()
        val xyzNs = XyzNs()
        val featureReader = JbFeature()
        var i = 0
        while (i < op_arr.size) {
            var id : String? = null
            try {
                val feature = feature_arr[i]
                val geo = geo_arr[i]
                val geo_type = geo_type_arr[i]
                val tags = tags_arr[i]
                val op = op_arr[i]
                opReader.mapBytes(op)
                featureReader.mapBytes(feature)
                val xyzOp = opReader.op()
                var uuid = opReader.uuid()
                id = opReader.id()
                if (id == null) {
                    id = featureReader.id()
                }
                if (id == null) throw NakshaException.forId(ERR_FEATURE_NOT_EXISTS, "Missing id", id)
                if (xyzOp == XYZ_OP_UPSERT) {
                    val query = "INSERT INTO $collectionId (id,feature,tags,geo_type,geo) VALUES($1,$2,$3,$4,$5) ON CONFLICT (id) DO UPDATE SET feature=$2,tags=$3,geo_type=$4,geo=$5 RETURNING xyz"
                    val rows = asArray(sql.execute(query, arrayOf(id, feature, tags, geo_type, geo)))
                    // TODO: What if no row is returned?
                    val xyz: ByteArray = asMap(rows[0])["xyz"]!!
                    xyzNs.mapBytes(xyz)
                    if (xyzNs.action() == ACTION_CREATE) {
                        table.returnCreated(id, xyz)
                    } else {
                        table.returnUpdated(id, xyz)
                    }
                } else if (xyzOp == XYZ_OP_CREATE) {
                    val query = "INSERT INTO $collectionId (id,feature,tags,geo_type,geo) VALUES($1,$2,$3,$4,$5) RETURNING xyz"
                    val rows = asArray(sql.execute(query, arrayOf(id, feature, tags, geo_type, geo)))
                    // TODO: What if no row is returned?
                    val xyz: ByteArray = asMap(rows[0])["xyz"]!!
                    table.returnCreated(id, xyz)
                } else {
                    throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Unsupported operation "+XyzOp.getOpName(xyzOp), id)
                }
                // TODO: Handle update, delete and purge
            } catch (e: NakshaException) {
                if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
                table.returnException(e)
            } catch (e: Exception) {
                if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
                table.returnErr(ERR_FATAL, e.rootCause().message ?: "Fatal", id)
            } finally {
                i++
            }
        }
        return table
    }

    fun writeCollections(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>
    ): ITable {
        errNo = null
        errMsg = null
        val sql = this.sql
        val table = sql.newTable()
        val opReader = XyzOp()
        val newCollection = NakshaCollection()
        var i = 0
        while (i < op_arr.size) {
            var id : String? = null
            try {
                val feature = feature_arr[i]
                val geo = geo_arr[i]
                val geo_type = geo_type_arr[i]
                val tags = tags_arr[i]
                val op = op_arr[i]
                opReader.mapBytes(op)
                var xyzOp = opReader.op()
                val uuid = opReader.uuid()
                id = opReader.id()
                if (id == null) id = newCollection.id()
                if (id == null) throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Missing id", id)
                val lockId = Static.lockId(id)
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(lockId))
                try {
                    newCollection.mapBytes(feature)
                    if (id != newCollection.id()) throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "ID in op does not match real feature id: $id != "+newCollection.id(), id)
                    var query = "SELECT id,feature,geo_type,geo,tags,xyz FROM naksha_collections WHERE id = $1"
                    var rows = asArray(sql.execute(query, arrayOf(id)))
                    var existing : IMap? = if (rows.isNotEmpty()) asMap(rows[0]) else null
                    query = "SELECT oid FROM pg_class WHERE relname = $1 AND relkind = ANY(array['r','p']) AND relnamespace = $2"
                    rows = asArray(sql.execute(query, arrayOf(id, schemaOid)))
                    val tableExists = rows.isNotEmpty()
                    if (xyzOp == XYZ_OP_UPSERT) xyzOp = if (existing != null) XYZ_OP_UPDATE else XYZ_OP_CREATE
                    if (xyzOp == XYZ_OP_CREATE) {
                        if (existing != null) throw NakshaException.forRow(ERR_CONFLICT, "Feature exists already", existing)
                        query = "INSERT INTO naksha_collections (id,feature,tags,geo_type,geo) VALUES($1,$2,$3,$4,$5) RETURNING xyz"
                        rows = asArray(sql.execute(query, arrayOf(id, feature, tags, geo_type, geo)))
                        if (rows.isEmpty()) throw NakshaException.forId(ERR_NO_DATA, "Failed to create collection for unknown reason", id)
                        val xyz: ByteArray = asMap(rows[0])["xyz"]!!
                        if (!tableExists) Static.collectionCreate(sql, schema, schemaOid, id, newCollection.pointsOnly(), newCollection.partition())
                        table.returnCreated(id, xyz)
                        continue
                    }
                    if (xyzOp == XYZ_OP_UPDATE) {
                        if (existing == null) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        if (uuid == null) {
                            // Override (not atomic) update.
                            query = "UPDATE naksha_collections SET feature=$1, tags=$2, geo_type=$3, geo=$4 WHERE id = $5 RETURNING xyz"
                            rows = asArray(sql.execute(query, arrayOf(feature, tags, geo_type, geo, id)))
                            if (rows.isEmpty()) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        } else {
                            // Atomic update.
                            query = "UPDATE naksha_collections SET feature=$1, tags=$2, geo_type=$3, geo=$4 WHERE id = $5 AND xyz_uuid(xyz) = $6 RETURNING xyz"
                            rows = asArray(sql.execute(query, arrayOf(feature, tags, geo_type, geo, id, uuid)))
                            if (rows.isEmpty()) {
                                query = "SELECT id,feature,geo_type,geo,tags,xyz FROM naksha_collections WHERE id = $1"
                                rows = asArray(sql.execute(query, arrayOf(id)))
                                existing = if (rows.isNotEmpty()) asMap(rows[0]) else null
                                if (existing != null) throw NakshaException.forRow(ERR_CONFLICT, "Collection is in different state", existing)
                                throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection $id does not exist", id)
                            }
                        }
                        val xyz: ByteArray = asMap(rows[0])["xyz"]!!
                        if (!tableExists) Static.collectionCreate(sql, schema, schemaOid, id, newCollection.pointsOnly(), newCollection.partition())
                        table.returnUpdated(id, xyz)
                        continue
                    }
                    if (xyzOp == XYZ_OP_DELETE) {
                        if (existing == null) {
                            table.returnRetained(id)
                            continue
                        }
                        if (uuid == null) {
                            // Override (not atomic) update.
                            query = "DELETE FROM naksha_collections WHERE id = $1 RETURNING id,feature,geo_type,geo,tags,xyz"
                            rows = asArray(sql.execute(query, arrayOf(id)))
                            if (rows.isEmpty()) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        } else {
                            // Atomic update.
                            query = "DELETE FROM naksha_collections WHERE id = $1 AND xyz_uuid(xyz) = $2 RETURNING id,feature,geo_type,geo,tags,xyz"
                            rows = asArray(sql.execute(query, arrayOf(id, uuid)))
                            if (rows.isEmpty()) {
                                query = "SELECT id,feature,geo_type,geo,tags,xyz FROM naksha_collections WHERE id = $1"
                                rows = asArray(sql.execute(query, arrayOf(id)))
                                existing = if (rows.isNotEmpty()) asMap(rows[0]) else null
                                if (existing != null) throw NakshaException.forRow(ERR_CONFLICT, "Collection is in different state", existing)
                                throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection $id does not exist", id)
                            }
                        }
                        existing = asMap(rows[0])
                        Static.collectionDrop(sql, id)
                        table.returnDeleted(existing)
                        continue
                    }
                    throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Operation for collection $id not supported: "+XyzOp.getOpName(xyzOp), id)
                } finally {
                    sql.execute("SELECT pg_advisory_unlock($1)", arrayOf(lockId))
                }
            } catch (e: NakshaException) {
                if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
                table.returnException(e)
            } catch (e: Exception) {
                if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
                table.returnErr(ERR_FATAL, e.rootCause().message ?: "Fatal", id)
            } finally {
                i++
            }
        }
        return table
    }

    private lateinit var featureReader: JbMapFeature
    private lateinit var propertiesReader: JbMap

    /**
     * Extract the id from the given feature.
     * @param feature The feature.
     * @return The id or _null_, if the feature does not have a dedicated id.
     */
    fun getFeatureId(feature: ByteArray): String? {
        if (!this::featureReader.isInitialized) featureReader = JbMapFeature()
        val reader = featureReader
        reader.mapBytes(feature)
        return reader.id()
    }

    /**
     * Extract the type of the feature by checking _properties.featureType_, _momType_ and _type_ properties in
     * that order. If none of these properties exist, returning _Feature_.
     * @param feature The feature to search in.
     * @return The feature type.
     */
    fun getFeatureType(feature: ByteArray): String {
        if (!this::featureReader.isInitialized) featureReader = JbMapFeature()
        val reader = featureReader
        reader.mapBytes(feature)
        val root = reader.root()
        if (root.selectKey("properties")) {
            val value = root.value()
            if (value.isMap()) {
                if (!this::propertiesReader.isInitialized) propertiesReader = JbMap()
                val properties = propertiesReader
                properties.mapReader(value)
                if (properties.selectKey("featureType")) {
                    val v = properties.value()
                    if (v.isText()) return v.readText()
                    if (v.isString()) return v.readString()
                }
            }
        }
        if (root.selectKey("momType") || root.selectKey("type")) {
            val value = root.value()
            if (value.isText()) return value.readText()
            if (value.isString()) return value.readString()
        }
        return "Feature"
    }
}