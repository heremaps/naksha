package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.objects.NakshaMap
import naksha.psql.PgIndex.PgIndexCompanion.gist_geo_2d
import naksha.psql.PgIndex.PgIndexCompanion.id_txn_uid
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral

/**
 * Information about the database and connection, that need only to be queried ones per session.
 * @constructor Creates and initializes a new database information object.
 * @param storage the PostgresQL storage in which this schema is stored.
 * @param mapId the schema name.
 */
@Suppress("MemberVisibilityCanBePrivate")
class PsqlAdminMap internal constructor(
    storage: PgStorage,
    config: PgConfig,
    create: Boolean?,
    upgrade: Boolean?
) : PgAdminMap(storage, config, create, upgrade) {

    private var clusterField: PgCluster? = null

    /**
     * The cluster.
     * @since 3.0.0
     */
    internal val cluster: PgCluster
        get() = clusterField ?: throw NakshaException(NakshaError.ILLEGAL_STATE, "Initialization not finished")

    override fun createAdminMap(config: PgConfig, storageId: String, storageNumber: Int64, psqlVersion: NakshaVersion): Int
        = upsertAdminMap(config, storageId, storageNumber, psqlVersion, null, null)

    override fun upgradeAdminMap(config: PgConfig, storageId: String, storageNumber: Int64, psqlVersion: NakshaVersion, schemaOid: Int, installedVersion: NakshaVersion?) {
        upsertAdminMap(config, storageId, storageNumber, psqlVersion, schemaOid, installedVersion)
    }

    override fun createPgMap(conn: PgConnection, map: NakshaMap): PgMap {
        TODO("Not yet implemented")
    }

    override fun deletePgMap(conn: PgConnection, map: PgMap) {
        TODO("Not yet implemented")
    }

    override fun getPgMapById(conn: PgConnection, id: String): PgMap? {
        TODO("Not yet implemented")
    }

    override fun getPgMapByNumber(conn: PgConnection, number: Int): PgMap? {
        TODO("Not yet implemented")
    }

    override fun listPgMaps(conn: PgConnection): PgMapList {
        TODO("Not yet implemented")
    }

    override fun getPgCollectionById(conn: PgConnection, map: PgMap, id: String): PgCollection? {
        TODO("Not yet implemented")
    }

    override fun getPgCollectionByNumber(conn: PgConnection, map: PgMap, number: Int): PgCollection? {
        TODO("Not yet implemented")
    }

    override fun listPgCollections(conn: PgConnection, map: PgMap): PgCollectionList {
        TODO("Not yet implemented")
    }

    override fun getEncodingFlags(feature: Any?, context: Any?): Flags {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictionary? {
        TODO("Not yet implemented")
    }

    override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? {
        TODO("Not yet implemented")
    }

    private fun upsertAdminMap(
        config: PgConfig,
        storageId: String,
        storageNumber: Int64,
        psqlVersion: NakshaVersion,
        schemaOid: Int?,
        installedVersion: NakshaVersion?
    ): Int {
        logger.info("Create cluster for storage '$storageId'")
        val master = PsqlInstance.get(config.masterUri)
        val replicas = mutableListOf<PgInstance>()
        for (replicaUri in config.replicaUris) {
            if (replicaUri == null) continue
            val replica = PsqlInstance.get(replicaUri)
            if (!replicas.contains(replica)) replicas.add(replica)
        }
        clusterField = PsqlCluster(master, replicas)

        var adminMapOid = schemaOid ?: 0
        val conn = cluster.newConnection(Naksha.adminOptions, false)
        conn.use {
            if (schemaOid == null) {
                logger.info("Create admin schema")
                conn.execute("CREATE SCHEMA IF NOT EXISTS \"naksha~admin\";").close()
                conn.execute("SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'naksha~admin'").use { cursor ->
                    adminMapOid = cursor["oid"]
                }
            }
            logger.info("Set search_path")
            conn.execute("SET SESSION search_path TO \"naksha~admin\", topology, hint_plan, public;").close()

            if (installedVersion == psqlVersion) {
                logger.info("Naksha admin map is up to date at version {}, do nothing", installedVersion)
                return adminMapOid // Kotlin should know, that the variable is not null!
            } else if (installedVersion != null) {
                logger.info("Naksha admin map is outdated, current installed version is {}, updating it to {}", installedVersion, psqlVersion)
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
                    "version" to (psqlVersion.toLong()).toString(),
                    "storageIdLiteral" to quoteLiteral(storageId),
                    "storageNumber" to storageNumber.toString()
                )
            )
            // Note: We reserve the first 1000 collection sequences for internal collections with hard-coded
            //       storage-numbers, because they have no entries in the naksha~collections table!
            logger.info("Create transaction-seq, map-sequence, and collection-sequence ...")
            conn.execute("CREATE SEQUENCE IF NOT EXISTS $NAKSHA_TXN_SEQ AS ${PgType.INT64} START 1 CACHE 10;").close()
            conn.execute("CREATE SEQUENCE IF NOT EXISTS $NAKSHA_MAP_SEQ AS ${PgType.INT64} START 1 CACHE 1;").close()
            conn.execute("CREATE SEQUENCE IF NOT EXISTS $NAKSHA_COL_SEQ AS ${PgType.INT64} START 100 CACHE 1;").close()

            logger.info("Create internal collections: transactions, collections, and dictionaries")
//            transactions.create_internal(
//                conn, 0, PgStorageClass.Consistent,
//                storeHistory = false,
//                storedDeleted = false,
//                storeMeta = true,
//                indices = listOf(
//                    id_txn_uid,
//                    gist_geo_2d,
//                    tags_id_txn_uid,
//                    app_id_updatedAt_id_txn_uid,
//                    author_ts_id_txn_uid
//                )
//            )
//            collections().create_internal(
//                conn, 0, PgStorageClass.Consistent,
//                storeHistory = true,
//                storedDeleted = true,
//                storeMeta = true,
//                indices = listOf(
//                    id_txn_uid,
//                    gist_geo_2d,
//                    tags_id_txn_uid,
//                    app_id_updatedAt_id_txn_uid,
//                    author_ts_id_txn_uid
//                )
//            )
//            dictionaries().create_internal(
//                conn, 0, PgStorageClass.Consistent,
//                storeHistory = true,
//                storedDeleted = true,
//                storeMeta = true,
//                indices = listOf(id_txn_uid, tags_id_txn_uid)
//            )
            logger.info("Done creating transactions, collections, and dictionaries")
        }
        return adminMapOid
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