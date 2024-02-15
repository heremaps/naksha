@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
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
    private val fnv1a = Fnv1a()

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and 255.
     */
    fun partitionNumber(id: String): Int {
        val fnv1a = this.fnv1a
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
            val versionString = row["version"] as String
            val firstSpace = versionString.indexOf(' ')
            check(firstSpace > 0)
            val secondSpace = versionString.indexOf(' ', firstSpace + 1)
            check(secondSpace > firstSpace)
            val pgv = versionString.substring(firstSpace + 1, secondSpace)
            pgVersion = XyzVersion.fromString(pgv)
        }
        return pgVersion
    }

    private fun ensureHistoryPartition() {

    }

    /**
     * Creates all necessary tables and structures, if not already existing.
     */
    fun initStorage() {
        val sql = this.sql
        sql.execute("CREATE SEQUENCE IF NOT EXISTS naksha_txn_uid_seq AS int8;")
        sql.execute("""
CREATE TABLE IF NOT EXISTS naksha_txn (
    txn         int8         PRIMARY KEY NOT NULL,
    ts          timestamptz  NOT NULL,
    xact_id     int8         NOT NULL,
    app_id      text         NOT NULL,
    author      text         NOT NULL,
    seq_id      int8,
    seq_ts      timestamptz,
    version     int8,
    details     bytea,
    attachment  bytea
) PARTITION BY RANGE (txn);
CREATE INDEX IF NOT EXISTS naksha_txn_ts_idx ON naksha_txn USING btree ("ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_app_id_ts_idx ON naksha_txn USING btree ("app_id" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_author_ts_idx ON naksha_txn USING btree ("author" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_id_idx ON naksha_txn USING btree ("seq_id" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_ts_idx ON naksha_txn USING btree ("seq_ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_version_idx ON naksha_txn USING btree ("version" ASC);
""")

        sql.execute("COMMIT")
    }

    // return: op text, id text, xyz bytea, tags bytea, geo geometry, feature bytea, err_no text, err_msg text
    fun writeFeatures(
            collectionId: String,
            ops: Array<ByteArray>,
            geometries: Array<Any?>,
            features: Array<ByteArray>,
            tags: Array<ByteArray>,
    ): ITable {
        errNo = null
        errMsg = null
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        val table = sql.newTable()
        // TODO: Implement me!
        // TODO implement validation checks

        val partitionId = partitionId("TODO")
        val tableName = collectionId //"""${collectionId}_p${partitionId}"""

        // prepared statements
        // id
        val selectHeadStmt = sql.prepare("""SELECT jsondata, geo FROM $tableName WHERE jb_get_text(jsondata, 'id', null)=$1;""", arrayOf("text"))
        // feature, geo
        val insertStmt = sql.prepare("""INSERT INTO $tableName (jsondata, geo) VALUES ($1, ST_Force3D($2)) RETURNING jsondata;""", arrayOf("bytea", "geometry"))
        // feature, geo, id
        val updateStmt = sql.prepare("""UPDATE $tableName SET jsondata=$1, geo=ST_Force3D($2) WHERE jb_get_text(jsondata, 'id', null)=$3 RETURNING jsondata;""", arrayOf("bytea", "geometry", "text"))
        // feature, geo, id, uuid
        val updateAtomicStmt = sql.prepare("""UPDATE $tableName SET jsondata=$1, geo=ST_Force3D($2) WHERE jb_get_text(jsondata, 'id', null)=$3 AND jb_get_text(jsondata, 'properties.@ns:com:here:xyz.uuid', null)=$4 RETURNING jsondata;""", arrayOf("bytea", "geometry", "text", "text"))
        // id
        val deleteStmt = sql.prepare("""DELETE FROM $tableName WHERE jb_get_text(jsondata, 'id', null)=$1 RETURNING jsondata, geo;""", arrayOf("text"))
        // id, uuid
        val deleteAtomicStmt = sql.prepare("""DELETE FROM $tableName WHERE jb_get_text(jsondata, 'id', null)=$1 AND jb_get_text(jsondata, 'properties.@ns:com:here:xyz.uuid', null)=$2 RETURNING jsondata, geo;""", arrayOf("text", "text"))
        /*
                // id
                val purgeStmt = sql.prepare("""DELETE FROM ${collectionId + "_del"} WHERE jb_get_text(jsondata, 'id', null)=$1 RETURNING jsondata, geo;""", arrayOf("text"))
                // id, uuid
                val purgeAtomicStmt = sql.prepare("""DELETE FROM ${collectionId + "_del"} WHERE jb_get_text(jsondata, 'id', null)=$1 AND jb_get_text(jsondata, 'properties.@ns:com:here:xyz.uuid', null)=$2 RETURNING jsondata, geo;""", arrayOf("text"))
                // id
                val selectDelStmt = sql.prepare("""SELECT jsondata, geo FROM ${collectionId + "_del"} WHERE jb_get_text(jsondata, 'id', null)=$1;""", arrayOf("text"))
        */
        var i = 0

        while (i < ops.size) {
            var r_op: String? = null
            var r_id: String? = null
            var r_feature: ByteArray? = null
            var r_geometry: Any? = null
            var r_err: String? = null

            val xyzOp = XyzOp().mapBytes(ops[i])
            var op = xyzOp.op()
            val feature = features[i]
            var id = xyzOp.id()
            val uuid = xyzOp.uuid()
            val geo = geometries[i]

            // Prefill result values, but they should be updated ones we have real values.
            r_id = id;

            if (op < 0 || op > 4) {
                r_op = "ERROR"
                // TODO
                r_err = "TODO return proper error"
                // TODO add to return table
                continue
            }
            if (op == 0) {
                val rows = sql.rows(insertStmt.execute(arrayOf(feature, geo)))!!
                r_feature = sql.cast(asMap(rows[0])["jsondata"]!!)
                // TODO GET DIAGNOSTICS rows_affected = ROW_COUNT;
                r_op = "CREATED"
            }

            // TODO exception handling

            // TODO rows_affected logic

            // TODO errors_only and min_result
            table.returnNext(
                    "op" to r_op,
                    "id" to r_id,
                    "xyz" to xyzOp,
                    "tags" to null,
                    "geo" to r_geometry,
                    "feature" to r_feature,
                    "err_no" to null,
                    "err_msg" to null
            )

            i += 1
        }

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