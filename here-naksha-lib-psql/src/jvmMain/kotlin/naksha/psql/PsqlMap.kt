package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.NakshaVersion
import naksha.model.NakshaException
import naksha.model.Naksha
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_SUPPORTED
import naksha.psql.PgIndex.PgIndexCompanion.app_id_updatedAt_id_txn_uid
import naksha.psql.PgIndex.PgIndexCompanion.author_ts_id_txn_uid
import naksha.psql.PgIndex.PgIndexCompanion.gist_geo_id_txn_uid
import naksha.psql.PgIndex.PgIndexCompanion.id_txn_uid
import naksha.psql.PgIndex.PgIndexCompanion.tags_id_txn_uid
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral

/**
 * Information about the database and connection, that need only to be queried ones per session.
 * @constructor Creates and initializes a new database information object.
 * @param storage the PostgresQL storage in which this schema is stored.
 * @param mapId the schema name.
 */
@Suppress("MemberVisibilityCanBePrivate")
class PsqlMap internal constructor(storage: PgStorage, mapId: String, schemaName: String) : PgMap(storage, mapId, schemaName) {
    override fun init(connection: PgConnection?) {
        Naksha.verifyId(id)
        val conn = connection ?: storage.newConnection(storage.adminOptions, false) { _, _ -> }
        try {
            init_internal(storage.id, conn)
            // auto-commit, if this is a temporary connection, and no exception
            if (connection == null) conn.commit()
        } finally {
            if (connection == null) conn.close()
        }
    }

    override fun init_internal(
        storageId: String?,
        conn: PgConnection,
        version: NakshaVersion,
        override: Boolean
    ): String {
        if (!isDefault()) throw NakshaException(MAP_NOT_SUPPORTED, "Only default map supported")
        _number = 0
        logger.info("Query database for identifier and version from {}, schema='{}'", conn, schemaName)
        conn.execute(
            """CREATE SCHEMA IF NOT EXISTS $nameQuoted;
SET SESSION search_path TO $nameQuoted, public, topology;"""
        ).close()
        var cursor = conn.execute(
            """
SELECT oid, null AS pronamespace, 'schema' AS proname FROM pg_namespace WHERE nspname = $1
UNION ALL
SELECT oid, pronamespace, proname FROM pg_proc WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = $1)
                                                 AND proname = ANY(ARRAY['naksha_version','naksha_storage_id']::text[]);
""", arrayOf(schemaName)
        )
        var has_naksha_version_fn = false
        var has_naksha_storage_id_fn = false
        cursor.use {
            while (cursor.next()) {
                val proname = cursor.column("proname")
                when (proname) {
                    "schema" -> _oid = cursor["oid"]
                    "naksha_version" -> has_naksha_version_fn = true
                    "naksha_storage_id" -> has_naksha_storage_id_fn = true
                }
            }
        }
        // If the storage has an ID, we need to guarantee that this function is called with correct id.
        var existingStorageId: String? = null
        var existingVersion: Int64? = null
        if (has_naksha_storage_id_fn) {
            val query = "SELECT naksha_storage_id() as id" + (if (has_naksha_version_fn) ", naksha_version() as v" else "null as v")
            cursor = conn.execute(query).fetch()
            existingStorageId = cursor.column("id") as String?
            existingVersion = cursor.column("v") as Int64?
        }
        val storage_id: String = if (existingStorageId != null) {
            if (storageId != null && storageId != existingStorageId) {
                throw NakshaException(STORAGE_ID_MISMATCH, "Expect $storageId, but found $existingStorageId")
            }
            existingStorageId
        } else storageId ?: PlatformUtil.randomString()

        if (existingStorageId == null && existingVersion == null) {
            logger.info("Schema '{}' does not exist, installing new fresh Naksha ...", schemaName)
        } else if (override) {
            logger.info("Force storage installation into schema '{}' via override parameter, ignore current state ...", schemaName)
        } else if ((existingStorageId != null) xor (existingVersion != null)) {
            logger.info(
                "Schema '{}' is in a broken state (id: {}, version: {}), updating it ...",
                schemaName, existingStorageId, if (existingVersion != null) NakshaVersion(existingVersion) else null
            )
        } else if (existingVersion == Int64(0)) {
            logger.info("Schema '{}' is installed with debug code (version=0), updating it ...")
        } else if (existingVersion == version.toInt64()) {
            logger.info("Schema '{}' is up to date (id: '{}', version: {}), do nothing", schemaName, storage_id, existingVersion)
            return storage_id
        } else {
            logger.info("Schema '{}' is installed with older ({}), updating it to {} ...", schemaName, existingVersion, version)
        }

        logger.info("Install/update module system of storage '{}', schema '{}' to version {}", storage_id, schemaName, version)
        val commonJs = getResourceAsText("/common.js")
        check(commonJs != null) { "Failed to load common.js from resources" }
        executeSqlFromResource(conn, "/common.sql", replacements = mapOf("common.js" to commonJs, "schema" to nameQuoted))

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
                "schemaIdent" to quoteIdent(schemaName),
                "schemaLiteral" to quoteLiteral(schemaName),
                "defaultSchemaLiteral" to quoteLiteral(storage.defaultSchemaName),
                "version" to (version.toLong()).toString(),
                "storageIdLiteral" to quoteLiteral(storage_id),
            )
        )
        // Note: We reserve the first 1000 collection sequences for internal collections with hard-coded
        //       storage-numbers, because they have no entries in the naksha~collections table!
        logger.info("Create collection-id sequence ...")
        conn.execute("CREATE SEQUENCE IF NOT EXISTS $NAKSHA_COL_SEQ AS ${PgType.INT64} START 1000 CACHE 1;").close()

        logger.info("Installation done ...")
        if (isDefault()) {
            logger.info("Creating the default schema, therefore create transaction sequences")
            conn.execute("""
CREATE SEQUENCE IF NOT EXISTS $NAKSHA_TXN_SEQ AS ${PgType.INT64} START 1 CACHE 10;
CREATE SEQUENCE IF NOT EXISTS $NAKSHA_MAP_SEQ AS ${PgType.INT64} START 1 CACHE 1;
""").close()
        }
        logger.info("Create internal collections: transactions, collections, and dictionaries")
        transactions().create_internal(
            conn, 0, PgStorageClass.Consistent,
            storeHistory = false,
            storedDeleted = false,
            storeMeta = true,
            indices = listOf(
                id_txn_uid,
                gist_geo_id_txn_uid,
                tags_id_txn_uid,
                app_id_updatedAt_id_txn_uid,
                author_ts_id_txn_uid
            )
        )
        collections().create_internal(
            conn, 0, PgStorageClass.Consistent,
            storeHistory = true,
            storedDeleted = true,
            storeMeta = true,
            indices = listOf(
                id_txn_uid,
                gist_geo_id_txn_uid,
                tags_id_txn_uid,
                app_id_updatedAt_id_txn_uid,
                author_ts_id_txn_uid
            )
        )
        dictionaries().create_internal(
            conn, 0, PgStorageClass.Consistent,
            storeHistory = true,
            storedDeleted = true,
            storeMeta = true,
            indices = listOf(id_txn_uid, tags_id_txn_uid)
        )
        logger.info("Done creating transactions, collections, and dictionaries")
        refresh(conn)
        return storage_id
    }

    private fun getResourceAsText(path: String): String? =
        this.javaClass.getResource(path)?.readText()

    /**
     * Replace all occurrences of `${key}` with `value`.
     * @param text the text in which to replace.
     * @param replacements a map where the key, expanded to `${key}`, should be replaced with the values.
     * @return the given text, but with replacements done.
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
     */
    private fun executeSqlFromResource(conn: PgConnection, path: String, replacements: Map<String, String>? = null) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        conn.execute(applyReplacements(resourceAsText, replacements)).close()
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
}