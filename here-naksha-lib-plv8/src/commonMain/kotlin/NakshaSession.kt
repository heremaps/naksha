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
        val initQuery = """SET SESSION search_path TO $quotedSchema, public, topology;"""
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
     * A cache to remember which collection configuration i.e. whether _hst is enabled or not
     * and the value is just _true_.
     */
    private lateinit var collectionConfiguration: IMap

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
        this.uid = 0
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

    private lateinit var gridPlan: IPlv8Plan

    /**
     * Create a GRID ([GeoHash](https://en.wikipedia.org/wiki/Geohash) Reference ID) from the given geometry.
     * The GRID is used for distributed processing of features. This method uses GeoHash at level 14, which uses
     * 34 bits for latitude and 36 bits for longitude (70-bit total). The precision is therefore higher than 1mm.
     *
     * If the feature does not have a geometry, this method creates a pseudo GRID (GeoHash Reference ID) from the
     * given feature-id, this is based upon [Geohash](https://en.wikipedia.org/wiki/Geohash#Textual_representation).
     *
     * See [https://www.movable-type.co.uk/scripts/geohash.html](https://www.movable-type.co.uk/scripts/geohash.html)
     * @param id The feature-id.
     * @param geoType The geometry type.
     * @param geo The feature geometry; if any.
     * @return The GRID (14 character long string).
     */
    internal fun grid(id: String, geoType: Short, geo: ByteArray?): String {
        if (geo == null) return Static.gridFromId(id)
        if (!this::gridPlan.isInitialized) {
            gridPlan = sql.prepare("SELECT ST_GeoHash(ST_Centroid(naksha_geometry($1::int2,$2::bytea)),14) as hash", arrayOf(SQL_INT16, SQL_BYTE_ARRAY))
        }
        return asMap(asArray(gridPlan.execute(arrayOf(geoType, geo)))[0])["hash"]!!
    }

    /**
     * Calculates the extent, the size of the feature in milliseconds.
     */
    internal fun extent(geo: ByteArray?): BigInt64 = Jb.int64.ZERO()
    // TODO: Implement me!

    private val xyzBuilder = XyzBuilder(Jb.env.newDataView(ByteArray(500)))

    /**
     * Convert the given database row into a JBON encoded XYZ namespace.
     * @param collectionId The collection for which to generate the XYZ namespace.
     * @param row The database row.
     * @return The XYZ namespace JBON encoded.
     */
    fun xyzNsFromRow(collectionId: String, row: IMap): ByteArray {
        val txn = NakshaTxn(row[COL_TXN]!!)
        val uid: Int = row[COL_UID]!!
        val uuid = txn.newFeatureUuid(storageId, collectionId, uid)
        val ptxn: BigInt64? = row[COL_PTXN]
        val puid: Int? = row[COL_PUID]
        val puuid = if (ptxn != null && puid != null) {
            NakshaTxn(ptxn).newFeatureUuid(storageId, collectionId, puid).toString()
        } else {
            null
        }
        return xyzBuilder.buildXyzNs(
                row[COL_CREATED_AT]!!,
                row[COL_UPDATE_AT]!!,
                txn.value,
                row[COL_ACTION]!!,
                row[COL_VERSION]!!,
                row[COL_AUTHOR_TS]!!,
                Jb.int64.ZERO(),
                puuid,
                uuid.toString(),
                row[COL_APP_ID]!!,
                row[COL_AUTHOR]!!,
                row[COL_GEO_GRID]!!
        )
    }

    /**
     * Session local uid counter.
     */
    private var uid = 0

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param NEW The row in which to update the XYZ namespace columns.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, NEW: IMap) {
        val txn = txn()
        val txnTs = txnTs()
        NEW[COL_TXN] = txn.value
        NEW[COL_TXN_NEXT] = Jb.int64.ZERO()
        NEW[COL_UID] = uid++
        NEW[COL_PTXN] = null
        NEW[COL_PUID] = null
        var geoType: Short? = NEW[COL_GEO_TYPE]
        if (geoType == null) {
            geoType = GEO_TYPE_NULL
            NEW[COL_GEO_TYPE] = GEO_TYPE_NULL
        }
        val geoGrid: String? = NEW[COL_GEO_GRID]
        if (geoGrid == null) {
            // Only calculate geo-grid, if not given by the client.
            val id: String? = NEW[COL_ID]
            check(id != null) { "Missing id" }
            NEW[COL_GEO_GRID] = grid(id, geoType, NEW[COL_GEOMETRY])
        }
        NEW[COL_ACTION] = ACTION_CREATE.toShort()
        NEW[COL_VERSION] = 1
        NEW[COL_CREATED_AT] = txnTs
        NEW[COL_UPDATE_AT] = txnTs
        NEW[COL_AUTHOR] = if (author != null) author else appId
        NEW[COL_AUTHOR_TS] = txnTs
        NEW[COL_APP_ID] = appId
    }

    /**
     *  Prepares XyzNamespace columns for head table.
     */
    internal fun xyzUpdateHead(collectionId: String, NEW: IMap, OLD: IMap) {
        xyzInsert(collectionId, NEW)
        NEW[COL_ACTION] = ACTION_UPDATE.toShort()
        NEW[COL_CREATED_AT] = OLD[COL_CREATED_AT]
        val oldVersion: Int = OLD[COL_VERSION]!!
        NEW[COL_VERSION] = oldVersion + 1
        NEW[COL_PTXN] = OLD[COL_TXN]
        NEW[COL_PUID] = OLD[COL_UID]
    }

    /**
     *  Updates XyzNamespace columns of OLD feature version, and moves it to _hst.
     */
    internal fun xyzUpdateHst(collectionId: String, NEW: IMap, OLD: IMap) {
        OLD[COL_TXN_NEXT] = NEW[COL_TXN]
        val isHstEnabled = true
        if (isHstEnabled) {
            // TODO move it outside and run it once
            val collectionIdQuoted = sql.quoteIdent("${collectionId}_hst")
            val hstInsertPlan = sql.prepare("""INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)""", COL_ALL_TYPES)
            hstInsertPlan.execute(arrayOf(OLD[COL_TXN_NEXT], OLD[COL_TXN], OLD[COL_UID], OLD[COL_PTXN], OLD[COL_PUID], OLD[COL_GEO_TYPE], OLD[COL_ACTION], OLD[COL_VERSION], OLD[COL_CREATED_AT], OLD[COL_UPDATE_AT], OLD[COL_AUTHOR_TS], OLD[COL_AUTHOR], OLD[COL_APP_ID], OLD[COL_GEO_GRID], OLD[COL_ID], OLD[COL_TAGS], OLD[COL_GEOMETRY], OLD[COL_FEATURE]))
        }
    }

    internal fun xyzDelete(): ByteArray {
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
            xyzInsert(collectionId, data.NEW)
        } else if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            xyzUpdateHead(collectionId, data.NEW, data.OLD)
        }
        // We should not be called for delete, in that case do nothing.
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [PgTrigger.NEW].
     */
    fun triggerAfter(data: PgTrigger) {
        var collectionId = data.TG_TABLE_NAME
        if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            xyzUpdateHst(collectionId, data.NEW, data.OLD)
        }
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
            if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(Static.TXN_LOCK_ID))
                try {
                    val raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnSeqOid)) as Array<Any>)[0])["v"])
                    txn = NakshaTxn(raw)
                    _txn = txn
                    if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
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
            if (hst_txn == null || hst_txn.year() != txn.year() || hst_txn.month() != txn.month() || hst_txn.day() != txn.day()) {
                historyPartitionCacheTxn = txn
                historyPartitionCache = Jb.map.newMap()
            }
            val tableName = "naksha_txn_${txn.historyPostfix()}"
            if (!Static.tableExists(sql, tableName, schemaOid)) {
                val start = NakshaTxn.of(txn.year(), txn.month(), txn.day(), SEQ_MIN)
                val end = NakshaTxn.of(txn.year(), txn.month(), txn.day(), SEQ_NEXT)
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
     * The start time of the transaction.
     * @return The start time of the transaction.
     */
    fun txnTs(): BigInt64 {
        txn()
        return _txts!!
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
        val sql = this.sql
        val table = sql.newTable()
        val opReader = XyzOp()
        val featureReader = JbFeature()
        val collectionIdQuoted = sql.quoteIdent(collectionId)
        val updatePlan = sql.prepare("""UPDATE $collectionIdQuoted
                        SET geo_grid=$2,geo_type=$3,geo=$4,tags=$5,feature=$6,action=$ACTION_UPDATE
                        RETURNING $COL_RETURN""",
                arrayOf(SQL_STRING, SQL_STRING, SQL_INT16, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY))
        val upsertPlan = sql.prepare("""INSERT INTO $collectionIdQuoted ($COL_WRITE) VALUES($1,$2,$3,$4,$5,$6)
                        ON CONFLICT (id) DO
                        UPDATE SET geo_grid=$2,geo_type=$3,geo=$4,tags=$5,feature=$6,action=$ACTION_UPDATE
                        RETURNING $COL_RETURN""",
                arrayOf(SQL_STRING, SQL_STRING, SQL_INT16, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY))

        val createPlan = sql.prepare("INSERT INTO $collectionIdQuoted ($COL_WRITE) VALUES($1,$2,$3,$4,$5,$6) RETURNING $COL_RETURN",
                arrayOf(SQL_STRING, SQL_STRING, SQL_INT16, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY))
        try {
            var i = 0
            while (i < op_arr.size) {
                var id: String? = null
                var xyzOp: Int? = null
                try {
                    val op = op_arr[i]
                    val feature = feature_arr[i]
                    val geo_type = geo_type_arr[i]
                    val geo = geo_arr[i]
                    val tags = tags_arr[i]

                    opReader.mapBytes(op)
                    xyzOp = opReader.op()
                    var uuid = opReader.uuid()
                    var grid = opReader.grid()
                    id = opReader.id()
                    if (id == null) {
                        featureReader.mapBytes(feature)
                        id = featureReader.id()
                    }
                    if (id == null) throw NakshaException.forId(ERR_FEATURE_NOT_EXISTS, "Missing id", id)
                    if (xyzOp == XYZ_OP_UPSERT) {
                        val rows = asArray(upsertPlan.execute(arrayOf(id, grid, geo_type, geo, tags, feature)))
                        // TODO: What if no row is returned?
                        val cols = asMap(rows[0])
                        val xyz = xyzNsFromRow(collectionId, cols)
                        if (ACTION_CREATE == cols[COL_ACTION]!!) {
                            table.returnCreated(id, xyz)
                        } else {
                            table.returnUpdated(id, xyz)
                        }
                    } else if (xyzOp == XYZ_OP_CREATE) {
                        val rows = asArray(createPlan.execute(arrayOf(id, grid, geo_type, geo, tags, feature)))
                        // TODO: What if no row is returned?
                        val xyz = xyzNsFromRow(collectionId, asMap(rows[0]))
                        table.returnCreated(id, xyz)
                    } else if (xyzOp == XYZ_OP_UPDATE) {
                        val rows = asArray(updatePlan.execute(arrayOf(id, grid, geo_type, geo, tags, feature)))
                        // TODO: What if no row is returned?
                        val xyz = xyzNsFromRow(collectionId, asMap(rows[0]))
                        table.returnUpdated(id, xyz)
                    } else {
                        throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Unsupported operation " + XyzOp.getOpName(xyzOp), id)
                    }
                    // TODO: Handle update, delete and purge
                } catch (e: NakshaException) {
                    if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
                    table.returnException(e)
                } catch (e: Throwable) {
                    handleFeatureException(e, table, id, xyzOp)
                } finally {
                    i++
                }
            }
        } finally {
            upsertPlan.free()
            updatePlan.free()
            createPlan.free()
        }
        return table
    }

    private fun handleFeatureException(e: Throwable, table: ITable, id: String?, op: Int?) {
        val err = asMap(e)
        // available fields: sqlerrcode, schema_name, table_name, column_name, datatype_name, constraint_name, detail, hint, context, internalquery, code
        val errCode: String? = err["sqlerrcode"]
        val tableName: String? = err["table_name"]
        when {
            errCode == ERR_UNIQUE_VIOLATION && op == XYZ_OP_CREATE -> {
                val existingFeature = asArray(sql.execute("SELECT * FROM $tableName WHERE id=$1", arrayOf(id)))[0]
                val featureAsMap = asMap(existingFeature)
                val nakshaException = NakshaException.forRow(ERR_UNIQUE_VIOLATION, "The feature with the id '$id' does exist already", featureAsMap, xyzNsFromRow(tableName!!, featureAsMap))
                table.returnException(nakshaException)
            }

            else -> {
                if (Static.PRINT_STACK_TRACES)
                    Jb.log.info(e.cause?.message!!)
                table.returnErr(ERR_FATAL, e.cause?.message ?: "Fatal ${e.stackTraceToString()}", id)
            }
        }
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
            var id: String? = null
            try {
                val feature = feature_arr[i]
                val geo = geo_arr[i]
                val geo_type = geo_type_arr[i]
                val tags = tags_arr[i]
                val op = op_arr[i]
                opReader.mapBytes(op)
                var xyzOp = opReader.op()
                val uuid = opReader.uuid()
                val grid = opReader.grid()
                id = opReader.id()
                if (id == null) id = newCollection.id()
                if (id == null) throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Missing id", id)
                val lockId = Static.lockId(id)
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(lockId))
                try {
                    newCollection.mapBytes(feature)
                    if (id != newCollection.id()) throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "ID in op does not match real feature id: $id != " + newCollection.id(), id)
                    var query = "SELECT $COL_ALL FROM naksha_collections WHERE $COL_ID = $1"
                    var rows = asArray(sql.execute(query, arrayOf(id)))
                    var existing: IMap? = if (rows.isNotEmpty()) asMap(rows[0]) else null
                    query = "SELECT oid FROM pg_class WHERE relname = $1 AND relkind = ANY(array['r','p']) AND relnamespace = $2"
                    rows = asArray(sql.execute(query, arrayOf(id, schemaOid)))
                    val tableExists = rows.isNotEmpty()
                    if (xyzOp == XYZ_OP_UPSERT) xyzOp = if (existing != null) XYZ_OP_UPDATE else XYZ_OP_CREATE
                    if (xyzOp == XYZ_OP_CREATE) {
                        if (existing != null) throw NakshaException.forRow(ERR_COLLECTION_EXISTS, "Feature exists already", existing, xyzNsFromRow(id, existing))
                        query = "INSERT INTO naksha_collections ($COL_WRITE) VALUES($1,$2,$3,$4,$5,$6) RETURNING $COL_RETURN"
                        rows = asArray(sql.execute(query, arrayOf(id, grid, geo_type, geo, tags, feature)))
                        if (rows.isEmpty()) throw NakshaException.forId(ERR_NO_DATA, "Failed to create collection for unknown reason", id)
                        if (!tableExists) Static.collectionCreate(sql, schema, schemaOid, id, newCollection.pointsOnly(), newCollection.partition())
                        val row = asMap(rows[0])
                        table.returnCreated(id, xyzNsFromRow(id, row))
                        continue
                    }
                    if (xyzOp == XYZ_OP_UPDATE) {
                        if (existing == null) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        if (uuid == null) {
                            // Override (not atomic) update.
                            query = "UPDATE naksha_collections SET grid=$1, geo_type=$2, geo=$3, feature=$4, tags=$5 WHERE id = $6 RETURNING $COL_RETURN"
                            rows = asArray(sql.execute(query, arrayOf(grid, geo_type, geo, feature, tags, id)))
                            if (rows.isEmpty()) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        } else {
                            // Atomic update.
                            // TODO: Fix me!
                            query = "UPDATE naksha_collections SET grid=$1, geo_type=$2, geo=$3, feature=$4, tags=$5 WHERE id = $6 AND txn = $7 AND uid = $8 RETURNING $COL_RETURN"
                            rows = asArray(sql.execute(query, arrayOf(grid, geo_type, geo, feature, tags, id, null, null)))
                            if (rows.isEmpty()) {
                                query = "SELECT id,feature,geo_type,geo,tags,xyz FROM naksha_collections WHERE id = $1"
                                rows = asArray(sql.execute(query, arrayOf(id)))
                                existing = if (rows.isNotEmpty()) asMap(rows[0]) else null
                                if (existing != null) throw NakshaException.forRow(ERR_CONFLICT, "Collection is in different state", existing, xyzNsFromRow(id, existing))
                                throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection $id does not exist", id)
                            }
                        }
                        val row = asMap(rows[0])
                        if (!tableExists) Static.collectionCreate(sql, schema, schemaOid, id, newCollection.pointsOnly(), newCollection.partition())
                        table.returnUpdated(id, xyzNsFromRow(id, row))
                        continue
                    }
                    if (xyzOp == XYZ_OP_DELETE) {
                        if (existing == null) {
                            table.returnRetained(id)
                            continue
                        }
                        if (uuid == null) {
                            // Override (not atomic) update.
                            query = "DELETE FROM naksha_collections WHERE id = $1 RETURNING $COL_ALL"
                            rows = asArray(sql.execute(query, arrayOf(id)))
                            if (rows.isEmpty()) throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", id)
                        } else {
                            // Atomic update.
                            // TODO: Fix me!
                            query = "DELETE FROM naksha_collections WHERE id = $1 AND txn = $2 AND uid = $3 RETURNING $COL_ALL"
                            rows = asArray(sql.execute(query, arrayOf(id, null, null)))
                            if (rows.isEmpty()) {
                                query = "SELECT id,feature,geo_type,geo,tags,xyz FROM naksha_collections WHERE id = $1"
                                rows = asArray(sql.execute(query, arrayOf(id)))
                                existing = if (rows.isNotEmpty()) asMap(rows[0]) else null
                                if (existing != null) throw NakshaException.forRow(ERR_CONFLICT, "Collection is in different state", existing, xyzNsFromRow(id, existing))
                                throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection $id does not exist", id)
                            }
                        }
                        existing = asMap(rows[0])
                        Static.collectionDrop(sql, id)
                        table.returnDeleted(existing)
                        continue
                    }
                    throw NakshaException.forId(ERR_INVALID_PARAMETER_VALUE, "Operation for collection $id not supported: " + XyzOp.getOpName(xyzOp), id)
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