@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.NakshaTxn.Companion.SEQ_MIN
import com.here.naksha.lib.plv8.NakshaTxn.Companion.SEQ_NEXT
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
         * The lock-id for the transaction number sequence.
         */
        val TXN_LOCK_ID = Naksha.lockId("naksha_txn_seq")

        /**
         * Tests if specific database table (in the Naksha session schema) exists already
         * @param sql The SQL API.
         * @param name The table name.
         * @param schemaOid The object-id of the schema to look into.
         * @return _true_ if a table with this name exists; _false_ otherwise.
         */
        fun tableExists(sql: IPlv8Sql, name: String, schemaOid:Int): Boolean {
            val rows = asArray(sql.execute("SELECT oid FROM pg_class WHERE relname = $1 AND relnamespace = $2", arrayOf(name, schemaOid)))
            return rows.isNotEmpty()
        }

        /**
         * Optimize the table storage configuration.
         * @param sql The SQL API.
         * @param tableName The table name.
         * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
         */
        fun collectionOptimizeTable(sql: IPlv8Sql, tableName: String, history: Boolean) {
            val quotedTableName = sql.quoteIdent(tableName)
            var query = """ALTER TABLE $quotedTableName
ALTER COLUMN feature SET STORAGE MAIN,
ALTER COLUMN geo SET STORAGE MAIN,
ALTER COLUMN tags SET STORAGE MAIN,
ALTER COLUMN xyz SET STORAGE MAIN,
SET (toast_tuple_target=8160"""
            query += if (history) ",fillfactor=100,autovacuum_enabled=OFF,toast.autovacuum_enabled=OFF"
            else """,fillfactor=50
-- Specifies the minimum number of updated or deleted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_threshold=10000, toast.autovacuum_vacuum_threshold=10000
-- Specifies the number of inserted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_insert_threshold=10000, toast.autovacuum_vacuum_insert_threshold=10000
-- Specifies a fraction of the table size to add to autovacuum_vacuum_threshold when deciding whether to trigger a VACUUM.
,autovacuum_vacuum_scale_factor=0.1, toast.autovacuum_vacuum_scale_factor=0.1
-- Specifies a fraction of the table size to add to autovacuum_analyze_threshold when deciding whether to trigger an ANALYZE.
,autovacuum_analyze_threshold=10000, autovacuum_analyze_scale_factor=0.1"""
            query += ")"
            sql.execute(query)
        }

        /**
         * Creates all the indices needed for a collection.
         * @param sql The SQL API.
         * @param tableName The table name.
         * @param spGist If SP-GIST index should be used, which is better only for point geometry.
         * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
         */
        fun collectionAddIndices(sql: IPlv8Sql, tableName: String, spGist: Boolean, history: Boolean) {
            val fillFactor = if (history) "100" else "50"
            // https://www.postgresql.org/docs/current/gin-tips.html
            val geoIndexType = if (spGist) "sp-gist" else "gist"
            val unique = if (history) "UNIQUE " else ""

            // quoted table name
            val qtn = sql.quoteIdent(tableName)
            // quoted index name
            var qin = sql.quoteIdent("${tableName}_id_idx")
            var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree 
((id) COLLATE "C" text_pattern_ops DESC) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_uid_idx")
            query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(uid) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_txn_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(xyz_txn(xyz) DESC) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_geo_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndexType
(geo, xyz_txn(xyz), xyz_extend(xyz)) WITH (buffering=ON,fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_tags_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin 
(tags_to_jsonb(tags), xyz_txn(xyz), xyz_extend(xyz)) WITH (fastupdate=ON,gin_pending_list_limit=32768);
"""

            qin = sql.quoteIdent("${tableName}_grid_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(xyz_grid(xyz) COLLATE "C" DESC, xyz_txn(xyz) DESC, xyz_extend(xyz)) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_mrid_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(xyz_mrid(xyz) COLLATE "C" DESC, xyz_txn(xyz) DESC, xyz_extend(xyz)) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_app_id_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(xyz_app_id(xyz) COLLATE "C" DESC, xyz_updated_at(xyz) DESC, xyz_txn(xyz) DESC) WITH (fillfactor=$fillFactor);
"""

            qin = sql.quoteIdent("${tableName}_author_idx")
            query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(xyz_author(xyz) COLLATE "C" DESC, xyz_author_ts(xyz) DESC, xyz_txn(xyz) DESC) WITH (fillfactor=$fillFactor);
"""
            sql.execute(query)
        }

        /**
         * Low level function to create a (optionally partitioned) collection table set.
         * @param sql The SQL API.
         * @param id The collection identifier.
         * @param spGist If SP-GIST index should be used, which is better only for point geometry.
         * @param partition If the collection should be partitioned.
         */
        fun collectionCreate(sql: IPlv8Sql, id: String, spGist: Boolean, partition: Boolean) {
            val CREATE_TABLE = """CREATE TABLE {table} (
    uid         int8,
    txn_next    int8,
    id          text COMPRESSION lz4,
    feature     bytea COMPRESSION lz4,
    geo         geometry(GeometryZ, 4326),
    tags        bytea COMPRESSION lz4,
    xyz         bytea COMPRESSION lz4
) """
            var query: String

            // HEAD
            val headNameQuoted = sql.quoteIdent(id)
            query = CREATE_TABLE.replace("{table}", headNameQuoted)
            if (!partition) {
                sql.execute(query)
                collectionOptimizeTable(sql, id, false)
                collectionAddIndices(sql, id, spGist, false)
            } else {
                query += "PARTITION BY RANGE (naksha_partition_number(id))"
                sql.execute(query)
                var i = 0
                while (i < 256) {
                    val partName = id + "_p" + Naksha.PARTITION_ID[i]
                    val partNameQuoted = sql.quoteIdent(partName)
                    query = "CREATE TABLE $partNameQuoted PARTITION OF $headNameQuoted FOR VALUES FROM ($i) "
                    i++
                    query += "TO ($i)"
                    sql.execute(query)
                    collectionOptimizeTable(sql, partName, false)
                    collectionAddIndices(sql, partName, spGist, false)
                }
            }

            // DEL.
            val delName = id + "_del"
            val delNameQuoted = sql.quoteIdent(delName)
            query = CREATE_TABLE.replace("{table}", delNameQuoted)
            sql.execute(query)
            collectionOptimizeTable(sql, delName, false)
            collectionAddIndices(sql, delName, spGist, false)

            // META.
            val metaName = id + "_meta"
            val metaNameQuoted = sql.quoteIdent(metaName)
            query = CREATE_TABLE.replace("{table}", metaNameQuoted)
            sql.execute(query)
            collectionOptimizeTable(sql, metaName, false)
            collectionAddIndices(sql, metaName, spGist, false)

            // HISTORY.
            val hstName = id + "_hst"
            val hstNameQuoted = sql.quoteIdent(hstName)
            query = CREATE_TABLE.replace("{table}", hstNameQuoted)
            query += "PARTITION BY RANGE (txn_next)"
            sql.execute(query)
            // Optimizations are done on the history partitions!
        }

        /**
         * Add the before and after triggers.
         * @param sql The SQL API.
         * @param id The collection identifier.
         */
        fun collectionAttachTriggers(sql: IPlv8Sql, id: String) {

        }

        /**
         * Deletes the collection with the given identifier.
         * @param sql The SQL API.
         * @param id The collection identifier.
         */
        fun collectionDrop(sql: IPlv8Sql, id: String) {
            require(!id.startsWith("naksha_"))
            val headName = sql.quoteIdent(id)
            val delName = sql.quoteIdent(id + "_del")
            val metaName = sql.quoteIdent(id + "_meta")
            val hstName = sql.quoteIdent(id + "_hst")
            sql.execute("""DROP TABLE IF EXISTS $headName CASCADE;
DROP TABLE IF EXISTS $delName CASCADE;
DROP TABLE IF EXISTS $metaName CASCADE;
DROP TABLE IF EXISTS $hstName CASCADE;""")
        }

        /**
         * Returns the current thread local [NakshaSession].
         * @return The current thread local [NakshaSession].
         * @throws IllegalStateException If the current session is no Naksha session.
         */
        @JvmStatic
        fun get(): NakshaSession {
            return threadLocal.get() as NakshaSession
        }

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
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(TXN_LOCK_ID))
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
                    sql.execute("SELECT pg_advisory_unlock($1)", arrayOf(TXN_LOCK_ID))
                }
            }
            // If the history partition cache does not exist or is outdated, initialize empty.
            val hst_txn = historyPartitionCacheTxn
            if (hst_txn == null || hst_txn.year != txn.year || hst_txn.month != txn.month || hst_txn.day != txn.day) {
                historyPartitionCacheTxn = txn
                historyPartitionCache = Jb.map.newMap()
            }
            val tableName = "naksha_txn_${txn.historyPostfix()}"
            if (!tableExists(sql, tableName, schemaOid)) {
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

    // return: op text, id text, xyz bytea, tags bytea, geo geometry, feature bytea, err_no text, err_msg text
    fun writeFeatures(
            collectionId: String,
            ops: Array<String>,
            ids: Array<String>,
            uuids: Array<String>,
            geometries: Array<Any?>,
            features: Array<ByteArray>,
            xyz: Array<ByteArray>
    ): ITable {
        errNo = null
        errMsg = null
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        val table = sql.newTable()
        // TODO: Implement me!
        return table
    }

    // return: op text, id text, xyz bytea, tags bytea, geo geometry, feature bytea, err_no text, err_msg text
    fun writeCollections(
            ops: Array<String>,
            ids: Array<String>,
            uuids: Array<String>,
            geometries: Array<Any?>,
            features: Array<ByteArray>,
            xyz: Array<ByteArray>
    ): ITable {
        errNo = null
        errMsg = null
        val table = sql.newTable()

        // TODO: Implement me!
        return table
    }
}