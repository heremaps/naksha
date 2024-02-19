@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import XyzOp
import XyzTags
import XyzVersion
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
@Suppress("UNUSED_PARAMETER", "unused", "LocalVariableName", "MemberVisibilityCanBePrivate")
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
    val txnOid: Int

    init {
        sql.execute("SET SESSION search_path TO " + sql.quoteIdent(schema) + ", public, topology;")
        schemaOid = asMap(asArray(sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))[0])["oid"]!!
        txnOid = asMap(asArray(sql.execute("SELECT oid FROM pg_class WHERE relname = 'naksha_txn_seq' and relnamespace = $1", arrayOf(schemaOid)))[0])["oid"]!!
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
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [PgTrigger.NEW].
     */
    fun triggerBefore(data: PgTrigger) {
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        // TODO: Implement me!
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [PgTrigger.NEW].
     */
    fun triggerAfter(data: PgTrigger) {
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        // TODO: Implement me!
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
     * Internally called to ensure that the [_txn] if valid and that the corresponding partition is created.
     */
    @Suppress("UNCHECKED_CAST")
    private fun verifyTxn() {
        if (_txn == null) {
            val row = asMap(asArray(sql.execute("SELECT pg_current_xact_id() xactid, nextval($1) txn, (extract(epoch from transaction_timestamp())*1000)::int8 as time", arrayOf(txnOid)))[0])
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
                    val raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnOid)) as Array<Any>)[0])["v"])
                    txn = NakshaTxn(raw)
                    _txn = txn
                    if (txn.year != txDate.year || txn.month != txDate.monthNumber || txn.day != txDate.dayOfMonth) {
                        // Rollover, we update sequence of the day.
                        txn = NakshaTxn.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, BigInt64(0))
                        _txn = txn
                        sql.execute("SELECT setval($1, $2)", arrayOf(txnOid, txn.value + 1))
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
                Jb.log.info("query: $query")
                sql.execute(query)
            }
        }
    }

    /**
     * Returns the current transaction number, if no transaction number is yet generated, generates a new one.
     * @return The current transaction number.
     */
    fun txn(): NakshaTxn {
        verifyTxn()
        return _txn!!
    }

    /**
     * Returns the PostgresQL transaction number ([xact_id](https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-PG-SNAPSHOT).
     * If no transaction number is yet generated, generates a new one.
     * @return The current PostgresQL transaction number.
     */
    fun xactId(): BigInt64 {
        verifyTxn()
        return _xactId!!
    }

    fun writeFeatures(
            collectionId: String,
            ops: Array<ByteArray>,
            features: Array<ByteArray>,
            geometries: Array<Any?>,
            tags: Array<ByteArray>
    ): ITable {
        errNo = null
        errMsg = null
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        val table = sql.newTable()
        // TODO: Implement me!
        return table
    }

    fun writeCollections(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray>,
            geo_arr: Array<Any?>,
            tags_arr: Array<ByteArray>
    ): ITable {
        errNo = null
        errMsg = null
        val sql = this.sql
        val table = sql.newTable()
        val xyzOp = XyzOp()
        val feature = JbFeature()
        val xyzTags = XyzTags()
        var i = 0
        while (i < op_arr.size) {
            try {
                xyzOp.mapBytes(op_arr[i])
                feature.mapBytes(feature_arr[i])
                xyzTags.mapBytes(tags_arr[i])
                val op = xyzOp.op()
                var id = xyzOp.id()
                if (id == null) {
                    id = feature.id()
                }
                if (id == null) throw NakshaException(ERR_ID_MISSING, "Missing id", xyzOpName(op), id, feature_arr[i], geo_arr[i], tags_arr[i])
                if (xyzOp.op() == XYZ_OP_CREATE || xyzOp.op() == XYZ_OP_UPSERT) {
                    // Try creation

                }
            } catch (e: NakshaException) {
                table.returnException(e)
            } catch (e: Exception) {
                table.returnErr(ERR_FATAL, e.message?: "Fatal")
            } finally {
                i++
            }
        }
        return table
    }
}