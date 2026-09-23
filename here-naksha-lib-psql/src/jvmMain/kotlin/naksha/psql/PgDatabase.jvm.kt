package naksha.psql

import naksha.base.ADMIN_CATALOG_QUOTED
import naksha.base.ADMIN_CATALOG_TEXT
import naksha.base.Action
import naksha.base.Id
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Version
import naksha.model.NakshaVersion
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral

actual open class PgDatabase actual constructor(storage: PgStorage, instance: PgInstance, id: Id)
    :AbstractPgDatabase(storage, instance, id)
{
    actual override fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int {
        val adminMapOid: Int = if (schema_oid != null) {
            schema_oid
        } else {
            logger.info("Create admin schema")
            conn.execute("CREATE SCHEMA IF NOT EXISTS $ADMIN_CATALOG_QUOTED;").close()
            conn.execute("SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'naksha~admin'").fetch().use { cursor ->
                cursor["oid"]
            }
        }
        logger.info("Set search_path")
        conn.execute("SET SESSION search_path TO $ADMIN_CATALOG_QUOTED, topology, hint_plan, public;").close()

        if (installed_version == psql_version) {
            logger.info("Naksha admin map is up to date at version {}, do nothing", installed_version)
            return adminMapOid
        } else if (installed_version != null) {
            logger.info("Naksha admin map is outdated, current installed version is {}, updating it to {}", installed_version, psql_version)
        } else {
            logger.info("Install new admin schema")
        }

        val commonJs = getResourceAsText("/common.js")
        check(commonJs != null) { "Failed to load common.js from resources" }
        executeSqlFromResource(conn, "/common.sql", replacements = mapOf("common.js" to commonJs))

        // Install default modules and SQL functions.
        installModuleFromResource(conn, "beautify", "/beautify.min.js", autoload = true)
        executeSqlFromResource(conn, "/beautify.sql")

        installModuleFromResource(conn, "lz4_util", "/lz4_util.js")
        installModuleFromResource(conn, "lz4_xxhash", "/lz4_xxhash.js")
        installModuleFromResource(conn, "lz4", "/lz4.js", beautify = false, autoload = true)
        executeSqlFromResource(conn, "/lz4.sql")

        installModuleFromResource(conn, "pako", "/pako.js", beautify = false, autoload = true)
        executeSqlFromResource(conn, "/pako.sql")

        // If the client initializes the module system, automatically load all these modules.
        // This is much faster eventually, because it will directly load all of them into the cache.
        installModuleFromResource(
            conn, "joda", "/js-joda.js",
            paths = arrayOf("@js-joda/core"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "kotlin",
            "/kotlin-kotlin-stdlib.mjs",
            paths = arrayOf("./kotlin-kotlin-stdlib.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn,
            "kotlinx_date_time",
            "/Kotlin-DateTime-library-kotlinx-datetime.mjs",
            paths = arrayOf("./Kotlin-DateTime-library-kotlinx-datetime.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "naksha_base",
            "/naksha_base.mjs",
            paths = arrayOf("./naksha_base.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "naksha_jbon",
            "/naksha_jbon.mjs",
            paths = arrayOf("./naksha_jbon.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "naksha_geo",
            "/naksha_geo.mjs",
            paths = arrayOf("./naksha_geo.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "naksha_model",
            "/naksha_model.mjs",
            paths = arrayOf("./naksha_model.mjs"),
            beautify = false,
            autoload = true
        )
        installModuleFromResource(
            conn, "naksha_psql",
            "/naksha_psql.mjs",
            paths = arrayOf("./naksha_psql.mjs"),
            beautify = false,
            autoload = true
        )
        logger.info("Installation of modules done, install naksha.sql ...")
        executeSqlFromResource(
            conn, "/naksha.sql", replacements = mapOf(
                "version" to (psql_version.toLong()).toString(),
                "storageIdLiteral" to quoteLiteral(id.text),
                "storageNumber" to id.number.toString()
            )
        )
        logger.info("Create version-sequence ...")
        // For a version number, the lower two bit must be always set.
        val START_VERSION = Version.now(0L, Action.VERSION)
        conn.execute("CREATE SEQUENCE IF NOT EXISTS $NAKSHA_VERSION_SEQ AS ${PgType.INT64} START ${START_VERSION.number} INCREMENT BY 4 CACHE 1;").close()

        logger.info("Create internal collections: collections, transactions, catalogs, and books")
        // TODO: We may not even need these hardcoded collections, they seem no longer relevant as specials.
        //       When we fixed this, we can just read them from the tables as normal rows?
        //       This unifies all collection handling.
        for (c in listOf(adminCollections, adminTransactions, adminCatalogs, adminBooks)) {
            create_table(conn, ADMIN_CATALOG_TEXT, c.id.text, c.columns, c.headIndices, c.shift, c.partitions)
            // TODO: Insert the rows with the collection features into `naksha~admin—>naksha~collections`.
        }
        // TODO: Insert the row with the admin-catalog feature into `naksha~admin—>naksha~catalogs`.
        // TODO: Eventually, we expect that these basic features are physically created.
        //       Later, caches will fetch them and users and list all catalogs and collections.
        //       By doing this we can get rid of all special handling, we treat them like normal collections.
        //       Beware that there will be no transaction for the initialization, but we could create it!
        //       We could even create a transaction log entry when upgrading!
        logger.info("Done creating internal collections")
        return adminMapOid
    }

    private fun getResourceAsText(path: String): String? =
        this.javaClass.getResource(path)?.readText()

    /**
     * Replace all occurrences of `${key}` with `value`.
     * @param text the text in which to replace.
     * @param replacements a map where the key, expanded to `${key}`, should be replaced with the values.
     * @return the given text, but with replacements done.
     * @since 3.0
     */
    private fun applyReplacements(text: String, replacements: Map<String, String>?): String {
        if (replacements != null) {
            var t = text
            val sb = StringBuilder()
            for (entry in replacements) {
                sb.setLength(0)
                sb.append('$').append('{').append(entry.key).append('}')
                val key = sb.toString()
                while (t.indexOf(key) >= 0) {
                    t = t.replace(key, entry.value, true)
                }
            }
            return t
        } else {
            return text
        }
    }

    /**
     * Execute the SQL being in the file.
     * @param conn The connection to use for the installation.
     * @param path The file-path, for example `/lz4.sql`.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     * @since 3.0
     */
    private fun executeSqlFromResource(conn: PgConnection, path: String, replacements: Map<String, String>? = null) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        val finalResourceAsText = applyReplacements(resourceAsText, replacements)
        conn.execute(finalResourceAsText).close()
    }

    /**
     * Install a JS module with the given name from the given resource file.
     * @param conn the connection to use for the installation.
     * @param name the module name, for example `lz4`.
     * @param path the file-path, for example `/lz4.js`.
     * @param paths an optional list of relative paths against with to allow to load the module as well.
     * @param autoload If the module should be automatically loaded.
     * @param beautify If the source should be beautified before insertion.
     * @param extraCode Additional code to be executed, appended at the end of the module.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     * @since 3.0
     */
    private fun installModuleFromResource(
        conn: PgConnection,
        name: String,
        path: String,
        paths: Array<String>? = null,
        autoload: Boolean = false,
        beautify: Boolean = false,
        extraCode: String? = null,
        replacements: Map<String, String>? = null
    ) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null) { "Failed to load resource from $path" }
        var code = applyReplacements(resourceAsText, replacements)
        if (extraCode != null) code += "\n" + extraCode
        val dollar4 = if (beautify) "js_beautify(\$4)" else "\$4"
        val query = "INSERT INTO es_modules (name, paths, autoload, source) VALUES (\$1, \$2, \$3, $dollar4) " +
                "ON CONFLICT (name) DO UPDATE SET paths=\$2, autoload=\$3, source=$dollar4"
        conn.execute(query, arrayOf(name, paths, autoload, code)).close()
    }

    /**
     * Bootstrap the admin-map, which means reading the cache initially, then start the background job to keep track of changes.
     * @since 3.0
     */
    internal fun start() {
    }
}