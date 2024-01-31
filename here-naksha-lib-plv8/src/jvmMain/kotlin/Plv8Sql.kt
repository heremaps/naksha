package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.ISql
import com.here.naksha.lib.jbon.ISqlPlan
import com.here.naksha.lib.jbon.ISqlResultSet
import java.sql.Connection

/**
 * Java JDBC binding to grant access to PostgresQL. Should be placed into [com.here.naksha.lib.jbon.JvmSession.sqlApi].
 */
class Plv8Sql(val conn: Connection) : ISql {
    // TODO: We need to initialize the connection.
    // If PLV8 is not yet installed, we need to install it.
    // This requires basically to test if the "commonjs2_modules" table exists already.
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

    override fun execute(sql: String, args: Array<Any?>): ISqlResultSet {
        TODO("Not yet implemented")
    }

    override fun prepare(sql: String, typeNames: Array<String>): ISqlPlan {
        TODO("Not yet implemented")
    }

    /**
     * Installs the commonjs2 code and all modules. Must only be executed ones per storage.
     */
    fun install() {
        // Execute the commonjs2 sql:
        // commonjs2.sql
        //
        // Then, install extensions
        // INSERT INTO commonjs2_modules (module, source) values ('lz4', quoteLiteral(file(lz4.js)))
        // INSERT INTO commonjs2_modules (module, source) values ('jbon', quoteLiteral(file(here-naksha-lib-jbon.js)))
        // INSERT INTO commonjs2_modules (module, source) values ('naksha', quoteLiteral(file(here-naksha-lib-plv8.js)))
        //
        // Finally, execute the extension SQL code:
        //
        // jbon.sql
        // plv8.sql
        //
        // Eventually all jbon_ and naksha_ methods are exposed to PostgresQL!
    }
}