@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
@Suppress("UNUSED_PARAMETER", "unused")
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
        fun get(): NakshaSession {
            return threadLocal.get() as NakshaSession
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
     * Internally used FNV1a hash.
     */
    private val fnv1a32 = Fnv1a32()

    /**
     * Internally used FNV1a hash.
     */
    private val fnv1a64 = Fnv1a64()

    /**
     * Returns the lock-id for the given name.
     * @param name The name to query the lock-id for.
     * @return The 64-bit FNV1a hash.
     */
    fun lockId(name: String): BigInt64 {
        fnv1a64.reset()
        fnv1a64.string(name)
        return fnv1a64.hash
    }

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and 255.
     */
    fun partitionNumber(id: String): Int {
        val fnv1a = this.fnv1a32
        fnv1a.reset()
        fnv1a.string(id)
        return fnv1a.hash and 0xff
    }

    /**
     * Returns the partition id as three digit string.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as three digit string.
     */
    fun partitionId(id: String): String = when (val partNumber = partitionNumber(id)) {
        in 0..9 -> "00$partNumber"
        in 10..99 -> "0$partNumber"
        else -> "$partNumber"
    }

    /**
     * Returns the partition from date.
     * @param year The year, e.g. 2024.
     * @param month The month between 1 (January) and 12 (December).
     * @param day The day between 1 and 31.
     * @return The name of the corresponding history partition.
     */
    fun historyPartitionFromDate(year: Int, month: Int, day: Int): String {
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
    fun historyPartitionFromMillis(millis: BigInt64): String {
        val ts = JbTimestamp.fromMillis(millis)
        return historyPartitionFromDate(ts.year, ts.month, ts.day)
    }

    /**
     * The last error number as SQLState.
     */
    var errNo: String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg: String? = null

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
     * The current transaction number.
     */
    private lateinit var txn: NakshaTxn

    /**
     * The PostgresQL transaction number.
     */
    private lateinit var xactId: BigInt64

    /**
     * Internally called to ensure that the trans
     */
    private fun createTxnPartition() {

    }

    /**
     * Returns the current transaction number, if no transaction number is yet generated, generates a new one.
     * @return The current transaction number.
     */
    @Suppress("UNCHECKED_CAST")
    fun txn(): NakshaTxn {
        if (!this::xactId.isInitialized) {
            xactId = asBigInt64(asMap((sql.execute("SELECT pg_current_xact_id() as v") as Array<Any>)[0])["v"])
            var raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnOid)) as Array<Any>)[0])["v"])
            txn = NakshaTxn(raw)
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            if (txn.year != now.year || txn.month != now.monthNumber || txn.day != now.dayOfMonth) {
                val lockId = lockId("naksha_txn_seq")
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(lockId))
                try {
                    raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnOid)) as Array<Any>)[0])["v"])
                    txn = NakshaTxn(raw)
                    if (txn.year != now.year || txn.month != now.monthNumber || txn.day != now.dayOfMonth) {
                        // Rollover, we update sequence of the day.
                        txn = NakshaTxn.of(now.year, now.monthNumber, now.dayOfMonth, BigInt64(0))
                        sql.execute("SELECT setval($1, $2)", arrayOf(txnOid, txn.value + 1))
                    }
                } finally {
                    sql.execute("SELECT pg_advisory_unlock($1)", arrayOf(lockId))
                }
            }
        }
        return txn
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
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        val table = sql.newTable()
        // TODO: Implement me!
        return table
    }
}