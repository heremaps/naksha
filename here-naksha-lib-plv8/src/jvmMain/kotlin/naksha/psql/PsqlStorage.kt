package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.Companion.logger
import naksha.base.PlatformObject
import naksha.jbon.*
import naksha.model.*
import naksha.model.NakshaCollectionProxy.Companion.PARTITION_COUNT_NONE
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.response.ErrorResponse
import naksha.model.response.Response
import naksha.model.response.Row
import naksha.model.response.SuccessResponse
import naksha.psql.Static.SC_DICTIONARIES
import naksha.psql.Static.SC_INDICES
import naksha.psql.Static.SC_TRANSACTIONS

/**
 * The Java implementation of the [IStorage] interface. This storage class is extended in `here-naksha-storage-psql`, which has a
 * `PsqlStoragePlugin` class, which implements the `Plugin` contract (internal contract in _Naksha-Hub_) and makes the storage available
 * to **Naksha-Hub** as storage plugin. It parses the configuration from feature properties given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property id the identifier of the storage.
 * @property cluster the PostgresQL instances used by this storage.
 * @property options the default connection options, ignores the [PgSessionOptions.readOnly] and [PgSessionOptions.useMaster].
 */
open class PsqlStorage(val id: String, val cluster: PsqlCluster, val options: PgSessionOptions) : PgStorage, IStorage {

    override fun id(): String = id

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
    override fun initStorage(params: Map<String, *>?) {
        var version = NakshaVersion.latest
        if (params != null && params.contains("version")) {
            val v = params["version"]
            version = when (v) {
                is String -> NakshaVersion.of(v)
                is Number -> NakshaVersion(v.toLong())
                is Int64 -> NakshaVersion(v)
                else -> throw IllegalArgumentException("params.version must be a valid Naksha version string or binary encoding")
            }
        }
        val pgSession = cluster.openSession(options)
        pgSession.use {
            pgSession.jdbcConnection.autoCommit = false
            logger.info("Initialize database {}", pgSession.jdbcConnection.url)
            val schemaQuoted = PgUtil.quoteIdent(options.schema)
            pgSession.execute(
                """
CREATE SCHEMA IF NOT EXISTS $schemaQuoted;
SET SESSION search_path TO $schemaQuoted, public, topology;
"""
            )
            val result = pgSession.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(options.schema))
            val rows = pgSession.rows(result)
            check(rows != null)
            check(rows.size == 1)
            val schemaOid: Int = rows[0]["oid"]!! as Int
            executeSqlFromResource(pgSession, "/commonjs2.sql")
            installModuleFromResource(pgSession, "beautify", "/beautify.min.js")
            executeSqlFromResource(pgSession, "/beautify.sql")
            pgSession.execute(
                """DO $$
var commonjs2_init = plv8.find_function("commonjs2_init");
commonjs2_init();
$$ LANGUAGE 'plv8';"""
            )
            installModuleFromResource(pgSession, "lz4_util", "/lz4_util.js")
            installModuleFromResource(pgSession, "lz4_xxhash", "/lz4_xxhash.js")
            installModuleFromResource(pgSession, "lz4", "/lz4.js")
            executeSqlFromResource(pgSession, "/lz4.sql")
            // Note: We know, that we do not need the replacements and code is faster without them!
            val replacements = mapOf("version" to version.toString(), "schema" to options.schema, "storage_id" to id)
            // Note: The compiler embeds the JBON classes into plv8.
            //       Therefore, we must not have it standalone, because otherwise we
            //       have two distinct instances in memory.
            //       A side effect sadly is that you need to require naksha, before you can require jbon!
            // TODO: Extend the commonjs2 code so that it allows to declare that one module contains another!
            installModuleFromResource(
                pgSession, "naksha", "/here-naksha-lib-plv8.js",
                beautify = true,
                replacements = replacements,
                extraCode = """
plv8.moduleCache["base"] = module.exports["here-naksha-lib-base"];
plv8.moduleCache["jbon"] = module.exports["here-naksha-lib-jbon"];
module.exports = module.exports["here-naksha-lib-plv8"];
"""
            )
            executeSqlFromResource(pgSession, "/naksha.sql", replacements)
            executeSqlFromResource(pgSession, "/jbon.sql")
            Static.createBaseInternalsIfNotExists(pgSession, options.schema, schemaOid)
            createInternalsIfNotExists()
            pgSession.commit()
        }
    }

    override fun convertRowToFeature(row: Row): NakshaFeatureProxy {
        return if (row.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbMapFeature(JbDictManager()).mapBytes(row.feature!!).reader
            val feature = JbMap().mapReader(featureReader).toIMap().proxy(NakshaFeatureProxy::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun convertFeatureToRow(feature: PlatformObject): Row {
        val nakshaFeature = feature.proxy(NakshaFeatureProxy::class)
        return Row(
            storage = this,
            flags = Flags.DEFAULT_FLAGS,
            id = nakshaFeature.id,
            feature = XyzBuilder().buildFeatureFromMap(nakshaFeature), // FIXME split feature to geo etc
            geoRef = null,
            geo = null,
            tags = null
        )
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    override fun newReadSession(context: NakshaContext, useMaster: Boolean): NakshaSession {
        return NakshaSession(this, context, options.copy(readOnly = true, useMaster = useMaster))
    }

    override fun newWriteSession(context: NakshaContext): NakshaSession {
        return NakshaSession(this, context, options.copy(readOnly = false, useMaster = true))
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
    }

    override fun openSession(context: NakshaContext, options: PgSessionOptions): PsqlSession {
        // TODO: We need to initialize the connection for the given context!
        return cluster.openSession(options)
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
     * @param conn The connection to use for the installation.
     * @param path The file-path, for example `/lz4.sql`.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    private fun executeSqlFromResource(conn: PsqlSession, path: String, replacements: Map<String, String>? = null) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        conn.execute(applyReplacements(resourceAsText, replacements))
    }

    /**
     * Install a JS module with the given name from the given resource file.
     * @param conn The connection to use for the installation.
     * @param name The module name, for example `lz4`.
     * @param path The file-path, for example `/lz4.js`.
     * @param autoload If the module should be automatically loaded.
     * @param beautify If the source should be beautified before insertion.
     * @param extraCode Additional code to be executed, appended at the end of the module.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    private fun installModuleFromResource(
        conn: PsqlSession,
        name: String,
        path: String,
        autoload: Boolean = false,
        beautify: Boolean = false,
        extraCode: String? = null,
        replacements: Map<String, String>? = null
    ) {
        val resourceAsText = getResourceAsText(path)
        check(resourceAsText != null)
        var code = applyReplacements(resourceAsText, replacements)
        if (extraCode != null) code += "\n" + extraCode
        val dollar3 = if (beautify) "js_beautify(\$3)" else "\$3"
        val query = "INSERT INTO commonjs2_modules (module, autoload, source) VALUES (\$1, \$2, $dollar3) " +
                "ON CONFLICT (module) DO UPDATE SET autoload = $2, source = $dollar3"
        conn.execute(query, arrayOf(name, autoload, code))
    }


    private fun createInternalsIfNotExists() {
        val verifyCreation: (Response) -> Unit = {
            assert(it is SuccessResponse) { (it as ErrorResponse).reason.message }
        }
        val nakshaSession = newWriteSession(NakshaContext.currentContext())

        val scTransaction = NakshaCollectionProxy(
            id = SC_TRANSACTIONS,
            partitions = PARTITION_COUNT_NONE,
            storageClass = SC_TRANSACTIONS,
            autoPurge = true,
            disableHistory = true
        )
        nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scTransaction)))).let(verifyCreation)

        val scDictionaries = NakshaCollectionProxy(
            id = SC_DICTIONARIES,
            partitions = PARTITION_COUNT_NONE,
            storageClass = SC_DICTIONARIES,
            autoPurge = false,
            disableHistory = false
        )
        nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scDictionaries)))).let(verifyCreation)

        val scIndices = NakshaCollectionProxy(
            id = SC_INDICES,
            partitions = PARTITION_COUNT_NONE,
            storageClass = SC_INDICES,
            autoPurge = false,
            disableHistory = false
        )
        nakshaSession.write(WriteRequest(arrayOf(WriteFeature(NKC_TABLE, feature = scIndices)))).let(verifyCreation)
    }
}