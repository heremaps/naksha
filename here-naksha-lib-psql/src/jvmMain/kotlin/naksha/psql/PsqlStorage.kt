package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformMap
import naksha.base.PlatformUtil
import naksha.base.fn.Fx2
import naksha.jbon.*
import naksha.model.*
import naksha.model.NakshaErrorCode.StorageErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.Row
import naksha.psql.PgUtil.PgUtilCompanion.ID
import naksha.psql.PgUtil.PgUtilCompanion.OPTIONS
import naksha.psql.PgUtil.PgUtilCompanion.VERSION
import naksha.psql.PgStatic.quote_ident
import naksha.psql.PgUtil.PgUtilCompanion.OVERRIDE
import java.util.concurrent.atomic.AtomicReference

/**
 * The Java implementation of the [IStorage] interface. This storage class is extended in `here-naksha-storage-psql`, which has a
 * `PsqlStoragePlugin` class, which implements the `Plugin` contract (internal contract in _Naksha-Hub_) and makes the storage available
 * to **Naksha-Hub** as storage plugin. It parses the configuration from feature properties given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property id the identifier of the storage.
 * @property cluster the PostgresQL instances used by this storage.
 * @property defaultOptions the default connection options, ignores the [PgOptions.readOnly] and [PgOptions.useMaster].
 */
open class PsqlStorage(override val cluster: PsqlCluster, override val defaultOptions: PgOptions) : PgStorage, IStorage {
    private var id: AtomicReference<String?> = AtomicReference(null)

    /**
     * The storage-id.
     * @throws IllegalStateException if [initStorage] has not been called before.
     */
    override fun id(): String {
        val id = this.id.get()
        check(id != null) { "PsqlStorage not initialized" }
        return id
    }

    /**
     * Connects to the configured PostgresQL database and verifies if the database is initialized.
     *
     * If the database is not initialized, it checks if the [current NakshaContext][NakshaContext.currentContext] is a supervisor
     * context, so the [NakshaContext.su] is _true_. If not, it will throw an [IllegalStateException].
     *
     * Eventually, when the database is not initialized and the current actor is a supervisor, it tries to install the
     * [PLV8 extension](https://plv8.github.io/), if being available. If that succeeds, it will install the `commonjs2`, `lz4`, `jbon`,
     * and `naksha` JavaScript modules.
     *
     * Generally, it will create the schema and all needed admin-tables (for transactions, global dictionary aso.). The server side
     * (database) code is only supported, if the database supports the [PLV8 extension](https://plv8.github.io/).
     *
     * @throws IllegalStateException if the data is not yet initialized, and the [current NakshaContext][NakshaContext.currentContext] is
     * not a supervisor context, so the [NakshaContext.su] is _false_.
     */
    @Suppress("UNCHECKED_CAST")
    override fun initStorage(params: Map<String, *>?) {
        if (this.id.get() != null) return
        synchronized(this) {
            if (this.id.get() != null) return
            var options = defaultOptions.copy(readOnly = false, useMaster = true)
            if (params != null && params.containsKey(OPTIONS)) {
                val v = params[OPTIONS]
                require(v is PgOptions) { "params.${OPTIONS} must be an instance of PgSessionOptions" }
                options = v
            }
            val initId: String? = if (params != null && params.containsKey(ID)) {
                val _id = params[ID]
                require(_id is String && _id.length > 0) { "params.${ID} must be a string with a minimal length of 1" }
                _id
            } else null
            var override = false
            if (params != null && params.containsKey(OVERRIDE)) {
                val v = params[OVERRIDE]
                require(v is Boolean) { "params.${OVERRIDE} must be a boolean, if given" }
                override = v
            }
            var version = NakshaVersion.latest
            if (params != null && params.contains(VERSION)) {
                val v = params[VERSION]
                version = when (v) {
                    is String -> NakshaVersion.of(v)
                    is Number -> NakshaVersion(v.toLong())
                    is Int64 -> NakshaVersion(v)
                    is NakshaVersion -> v
                    else -> throw IllegalArgumentException("params.${VERSION} must be a valid Naksha version string or binary encoding")
                }
            }
            val conn = cluster.newConnection(options)
            conn.use {
                logger.info("Start init of database {}", conn.jdbc.url)
                conn.jdbc.autoCommit = false

                logger.info("Query database for identifier and version", conn.jdbc.url)
                val schemaQuoted = PgUtil.quoteIdent(options.schema)
                conn.execute(
                    """
CREATE SCHEMA IF NOT EXISTS $schemaQuoted;
SET SESSION search_path TO $schemaQuoted, public, topology;
"""
                ).close()
                var cursor = conn.execute(
                    """
SELECT null as oid, oid AS pronamespace, null AS proname FROM pg_namespace WHERE nspname = $1
union all
SELECT oid, pronamespace, proname FROM pg_proc WHERE 
    pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = $1)
AND proname = ANY(ARRAY['naksha_version','naksha_storage_id']::text[]);
""", arrayOf(options.schema)
                ).fetch()
                val schemaOid: Int
                var has_naksha_version_fn = false
                var has_naksha_storage_id_fn = false
                cursor.use {
                    // Note: We have
                    schemaOid = cursor["pronamespace"]
                    while (cursor.isRow()) {
                        val proname = cursor.column("proname")
                        when (proname) {
                            "naksha_version" -> has_naksha_version_fn = true
                            "naksha_storage_id" -> has_naksha_storage_id_fn = true
                        }
                        cursor.fetch()
                    }
                }
                // If the storage has an ID, we need to guarantee that this function is called with correct id.
                var existingStorageId: String? = null
                var existingVersion: Int64? = null
                if (has_naksha_storage_id_fn) {
                    val query = "SELECT naksha_storage_id() as id"+(if (has_naksha_version_fn) ", naksha_version() as v" else "null as v")
                    cursor = conn.execute(query).fetch()
                    existingStorageId = cursor.column("id") as String?
                    existingVersion = cursor.column("v") as Int64?
                }
                val storageId: String = if (existingStorageId != null) {
                    if (initId != null && initId != existingStorageId) {
                        throw StorageException(NakshaError(STORAGE_ID_MISMATCH, "Expect $initId, but found $existingStorageId"))
                    }
                    existingStorageId
                } else initId ?: PlatformUtil.randomString()

                if (override) {
                    logger.info("Force storage installation via override parameter, ignore current state ...")
                } else if (existingVersion == null) {
                    logger.info("Storage is in broken state, it has an identifier, but no version, updating it ...")
                } else if (existingVersion == Int64(0)) {
                    logger.info("Storage is installed with debug code (version=0), updating it ...")
                } else if (existingVersion == version.toInt64()) {
                    logger.info("Storage is up to date, id: $storageId, version: $existingVersion, do nothing")
                    this.id.set(storageId)
                    return
                }

                logger.info("Install/update module system of storage '$storageId' to version $version")
                val commonJs = getResourceAsText("/common.js")
                check(commonJs != null) { "Failed to load common.js from resources" }
                executeSqlFromResource(conn, "/common.sql", replacements = mapOf("common.js" to commonJs, "schema" to schemaQuoted))

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
                conn.commit()
                this.id.set(storageId)

                // Note: We know, that we do not need the replacements and code is faster without them!
                val replacements = mapOf(VERSION to version.toInt64().toString(), "schema" to options.schema, "storage_id" to initId)
                executeSqlFromResource(conn, "/naksha.sql", replacements as Map<String,String>)
                PgStatic.createBaseInternalsIfNotExists(conn, options.schema, schemaOid)
                conn.commit()

//                val nakshaSession = newWriteSession(NakshaContext.currentContext())
//
//                val scTransaction = NakshaCollectionProxy(
//                    id = TRANSACTIONS_COL,
//                    partitions = PARTITION_COUNT_NONE,
//                    storageClass = TRANSACTIONS_COL,
//                    autoPurge = true,
//                    disableHistory = true
//                )
//                nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scTransaction)))).let(verifyCreation)
//
//                val scDictionaries = NakshaCollectionProxy(
//                    id = DICTIONARIES_COL,
//                    partitions = PARTITION_COUNT_NONE,
//                    storageClass = DICTIONARIES_COL,
//                    autoPurge = false,
//                    disableHistory = false
//                )
//                nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scDictionaries)))).let(verifyCreation)
//
//                val scIndices = NakshaCollectionProxy(
//                    id = INDICES_COL,
//                    partitions = PARTITION_COUNT_NONE,
//                    storageClass = INDICES_COL,
//                    autoPurge = false,
//                    disableHistory = false
//                )
//                nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scIndices)))).let(verifyCreation)
            }
        }
    }

    override fun rowToFeature(row: Row): NakshaFeatureProxy {
        return if (row.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbMapFeatureDecoder(JbDictManager()).mapBytes(row.feature!!).reader
            val feature = JbMapDecoder().mapReader(featureReader).toIMap().proxy(NakshaFeatureProxy::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun featureToRow(feature: PlatformMap): Row {
        val nakshaFeature = feature.proxy(NakshaFeatureProxy::class)
        return Row(
            storage = this,
            flags = Flags(),
            id = nakshaFeature.id,
            feature = XyzEncoder().buildFeatureFromMap(nakshaFeature), // FIXME split feature to geo etc
            geoRef = null,
            geo = null,
            tags = null
        )
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    /**
     * Returns a new PostgresQL session.
     * @param options the options to use for the database connection used by this session.
     * @return the session.
     */
    override fun newSession(options: PgOptions): PgSession = PgSession(this, options)

    /**
     * Opens a new PostgresQL database connection. A connection received through this method will not really close when
     * [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance.
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * the previous returned connection will always cause an [IllegalStateException].
     * @param options the options for the session; defaults to [defaultOptions].
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the
     * connection. It may just do the work or perform arbitrary additional work.
     * @throws IllegalStateException if all connections are in use.
     */
    override fun newConnection(options: PgOptions, init: Fx2<PgConnection, String>?): PgConnection {
        val conn = cluster.newConnection(options)
        val quotedSchema = if (options.schema == defaultOptions.schema) this.quotedSchema else quote_ident(options.schema)
        // TODO: Do we need more initialization work here?
        val query = "SET SESSION search_path TO $quotedSchema, public, topology;\n"
        if (init != null) init.call(conn, query) else conn.execute(query).close()
        return conn
    }


    private var _quotedSchema: String? = null
    val quotedSchema: String
        get() {
            var s = _quotedSchema
            if (s == null) {
                s = quote_ident(defaultOptions.schema)
                _quotedSchema = s
            }
            return s
        }

    private var pgInfo: PgInfo? = null

    override fun getPgDbInfo(): PgInfo {
        var pgDbInfo = this.pgInfo
        if (pgDbInfo == null) {
            val conn = cluster.newConnection(defaultOptions.copy(readOnly = false, useMaster = true))
            conn.use {
                pgDbInfo = PgInfo(conn, defaultOptions.schema)
                this.pgInfo = pgDbInfo
            }
        }
        return pgDbInfo!!
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
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
    private fun executeSqlFromResource(conn: PsqlConnection, path: String, replacements: Map<String, String>? = null) {
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
        conn: PsqlConnection,
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
