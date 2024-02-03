@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.JbSession
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
 * @property appName The name of the application starting the session, only for debugging purpose.
 * @property streamId The stream-identifier, to be added to the transaction logs for debugging purpose.
 * @property appId The UPM identifier of the application (for audit).
 * @property author The UPM identifier of the user (for audit).
 * @constructor Create a new session.
 */
@Suppress("UNUSED_PARAMETER", "unused")
@JsExport
class NakshaSession(val sql: IPlv8Sql, appName: String, streamId: String, appId: String, author: String? = null) :
        JbSession(appName, streamId, appId, author) {

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
     * Returns the partition id as three digit string.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as three digit string.
     */
    fun partitionId(id:String) : String {
        TODO("Implement me!")
    }

    /**
     * The last error number as SQLState.
     */
    var errNo : String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg : String? = null

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