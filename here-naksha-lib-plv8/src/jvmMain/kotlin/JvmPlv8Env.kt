package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import java.sql.Connection

/**
 * Simulates an PLV8 environment to allow better testing. Technically, except for the [install] method, this class
 * is never needed in `lib-psql` or other Java code, the Java code should always rely upon the SQL functions.
 *
 * This class extends the standard [JvmEnv] by SQL support to simulate the internal database connection of PLV8.
 */
class JvmPlv8Env : JvmEnv() {
    companion object {
        private lateinit var env : JvmPlv8Env

        @JvmStatic
        fun initialize() {
            if (!Jb.isInitialized()) JvmEnv.initialize()
            if (!this::env.isInitialized) {
                env = JvmPlv8Env()
                Jb.env = env
            }
        }

        /**
         * Returns the current environment, if not available, initializes it and then returns it.
         */
        @JvmStatic
        fun get(): JvmPlv8Env {
            initialize()
            return Jb.env as JvmPlv8Env
        }
    }

    // ============================================================================================================
    //                             Additional code only available in Java
    // ============================================================================================================

    /**
     * Simulates the SQL function `naksha_start_session`. Creates the [NakshaSession] can binds the [NakshaSession.sql]
     * to the given SQL connection, so that all other session methods work the same way they would normally behave
     * inside the Postgres database (so, when executed in PLV8 engine).
     * @param conn The connection to bind to the [NakshaSession].
     * @param schema The schema to use.
     * @param appName The name of this application (arbitrary string, seen in SQL connections).
     * @param streamId The logging stream-id, can be found in transaction logs for error search.
     * @param appId The UPM application identifier.
     * @param author The UPM user identifier that uses this session.
     */
    fun startSession(conn: Connection, schema: String, appName: String, streamId: String, appId: String, author: String?) {
        // Prepare the connection, need to load the module system.
        conn.autoCommit = false
        val sql = JvmPlv8Sql(conn)
        val schemaQuoted = sql.quoteIdent(schema)
        sql.execute("SET SESSION search_path TO $schemaQuoted, public, topology; SELECT naksha_start_session($1, $2, $3, $4);",
                arrayOf(appName, streamId, appId, author))
        conn.commit()
        // Create our self.
        get()
        // Create the JVM Naksha session.
        val data = asMap(asArray(sql.execute("SELECT naksha_storage_id() as storage_id, naksha_schema() as schema"))[0])
        val session = NakshaSession(sql, data["schema"]!!, data["storage_id"]!!, appName, streamId, appId, author)
        JbSession.threadLocal.set(session)
    }

    /**
     * Returns the JDBC connection of this session.
     */
    fun getConnection(): Connection {
        val naksha = NakshaSession.get()
        val sql = naksha.sql
        check(sql is JvmPlv8Sql)
        val conn = sql.conn
        check(conn != null)
        return conn
    }

    /**
     * Ends the JVM session and returns the assigned connection.
     * @return The connection, if still alive.
     */
    fun endSession(): Connection? {
        val session = JbSession.get()
        val conn: Connection?
        if (session is NakshaSession) {
            val sql = session.sql
            conn = if (sql is JvmPlv8Sql) sql.conn else null
        } else {
            conn = null
        }
        JbSession.threadLocal.set(null)
        return conn
    }

    private fun getResourceAsText(path: String): String? =
            object {}.javaClass.getResource(path)?.readText()

    private fun applyReplacements(text: String, replacements: Map<String, String>?): String {
        if (replacements != null) {
            var t = text
            val sb = StringBuilder()
            for (entry in replacements) {
                sb.setLength(0)
                sb.append('$').append('{').append(entry.key).append('}')
                t = t.replace(sb.toString(), entry.value, true)
            }
            return t
        } else {
            return text
        }
    }

    /**
     * Execute the SQL being in the file.
     * @param sql The connection to use for the installation.
     * @param path The file-path, for example `/lz4.sql`.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    private fun executeSqlFromResource(sql: JvmPlv8Sql, path: String, replacements: Map<String, String>? = null) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        sql.execute(applyReplacements(resourceAsText, replacements))
    }

    /**
     * Install a JS module with the given name from the given resource file.
     * @param sql The connection to use for the installation.
     * @param name The module name, for example `lz4`.
     * @param path The file-path, for example `/lz4.js`.
     * @param autoload If the module should be automatically loaded.
     * @param extraCode Additional code to be executed, appended at the end of the module.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    private fun installModuleFromResource(sql: JvmPlv8Sql, name: String, path: String, autoload: Boolean = false, extraCode: String? = null, replacements: Map<String, String>? = null) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        var code = applyReplacements(resourceAsText, replacements)
        if (extraCode != null) code += "\n" + extraCode
        sql.execute("INSERT INTO commonjs2_modules (module, autoload, source) VALUES ($1, $2, $3) " +
                "ON CONFLICT (module) DO UPDATE SET autoload = $2, source = $3",
                arrayOf(name, autoload, code))
    }

    /**
     * Installs the `commonjs2`, `lz4`, `jbon` and `naksha` modules into a PostgresQL database with a _PLV8_ extension. Must only
     * be executed ones per storage. This as well creates the needed admin-tables (for transactions, global dictionary aso.).
     * @param conn The connection to use for the installation.
     * @param version The Naksha Version.
     */
    fun install(conn: Connection, version: Long, schema: String, storageId: String) {
        conn.autoCommit = false
        val sql = JvmPlv8Sql(conn)
        val replacements = mapOf("version" to version.toString(), "schema" to schema, "storage_id" to storageId)
        // Note: We know, that we do not need the replacements and code is faster without them!
        executeSqlFromResource(sql, "/commonjs2.sql")
        installModuleFromResource(sql, "lz4_util", "/lz4_util.js")
        installModuleFromResource(sql, "lz4_xxhash", "/lz4_xxhash.js")
        installModuleFromResource(sql, "lz4", "/lz4.js")
        executeSqlFromResource(sql, "/lz4.sql")
        // Note: The compiler embeds the JBON classes into plv8.
        //       Therefore, we must not have it standalone, because otherwise we
        //       have two distinct instances in memory.
        //       A side effect sadly is that you need to require naksha, before you can require jbon!
//        installModuleFromResource(sql, "jbon", "/here-naksha-lib-jbon.js", extraCode = """
//module.exports = module.exports["here-naksha-lib-jbon"].com.here.naksha.lib.jbon;
//""");
        installModuleFromResource(sql, "naksha", "/here-naksha-lib-plv8.js",
                replacements = replacements,
                extraCode = """
plv8.moduleCache["jbon"] = module.exports["here-naksha-lib-plv8"].com.here.naksha.lib.jbon;
module.exports = module.exports["here-naksha-lib-plv8"].com.here.naksha.lib.plv8;
""")
        executeSqlFromResource(sql, "/naksha.sql", replacements)
        executeSqlFromResource(sql, "/jbon.sql")
        Static.initStorage(sql, schema)
        conn.commit()
    }
}