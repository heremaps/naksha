package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.JvmSql
import java.sql.Connection

/**
 * Extends the standard JVM connection handling with special functions needed for Naksha PLV8 code.
 */
class Plv8Sql(conn: Connection) : JvmSql(conn) {

    /**
     * Invokes the `naksha_start_session` SQL method.
     * @param appName The name of the application.
     * @param appId The UPM identifier of the application.
     * @param author The UPM identifier of the author.
     * @param streamId A unique identifier for the session for logging purpose.
     */
    fun startSession(appName: String, appId: String, author: String?, streamId: String?) {
        execute("SELECT naksha_start_session($1, $2, $3, $4)", arrayOf(appName, appId, author, streamId))
    }
}