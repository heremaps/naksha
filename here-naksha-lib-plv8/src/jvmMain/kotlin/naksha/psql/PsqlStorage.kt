package naksha.psql

import naksha.base.Int64
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
import naksha.psql.*
import naksha.psql.NKC_TABLE
import naksha.psql.Static.SC_DICTIONARIES
import naksha.psql.Static.SC_INDICES
import naksha.psql.Static.SC_TRANSACTIONS

/**
 * The Java implementation of the [IStorage] interface. This storage class is extended in `here-naksha-storage-psql`, which has a
 * `PsqlStoragePlugin` class, which implements the `Plugin` contract (internal contract in _Naksha-Hub_) and makes the storage available
 * to **Naksha-Hub** as storage plugin. It parses the configuration from feature properties given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property id the identifier of the storage.
 * @property pgCluster the PostgresQL instances used by this storage.
 * @property schema the schema to use.
 */
open class PsqlStorage(val id: String, val pgCluster: PsqlCluster, val schema: String) : PgStorage, IStorage {

    override fun id(): String = id

    override fun initStorage(params: Map<String, *>?) {
        val conn = pgCluster.openSession(PgSessionOptions(false))
        conn.use {
            install(conn, 0, schema, id, appName = "fixme")
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
        return NakshaSession(this, context, PgSessionOptions(true), schema)
    }

    override fun newWriteSession(context: NakshaContext): NakshaSession {
        return NakshaSession(this, context, PgSessionOptions(false), schema)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
    }

    override fun openSession(context: NakshaContext, options: PgSessionOptions): PsqlSession {
        // TODO: We need to initialize the connection for the given context!
        return pgCluster.openSession(options)
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

    /**
     * Installs the `commonjs2`, `lz4`, `jbon` and `naksha` modules into a PostgresQL database with a _PLV8_ extension. Must only
     * be executed ones per storage. This as well creates the needed admin-tables (for transactions, global dictionary aso.).
     * @param conn The connection to use for the installation.
     * @param version The Naksha Version.
     */
    fun install(conn: PsqlSession, version: Long, schema: String, storageId: String, appName: String) {
        conn.pgConnection.autoCommit = false
        val schemaQuoted = PgUtil.quoteIdent(schema)
        conn.execute(
            """
CREATE SCHEMA IF NOT EXISTS $schemaQuoted;
SET SESSION search_path TO $schemaQuoted, public, topology;
"""
        )
        val schemaOid: Int = conn.rows(conn.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))!![0]["oid"]!! as
                Int

        executeSqlFromResource(conn, "/commonjs2.sql")
        installModuleFromResource(conn, "beautify", "/beautify.min.js")
        executeSqlFromResource(conn, "/beautify.sql")
        conn.execute(
            """DO $$
var commonjs2_init = plv8.find_function("commonjs2_init");
commonjs2_init();
$$ LANGUAGE 'plv8';"""
        )
        installModuleFromResource(conn, "lz4_util", "/lz4_util.js")
        installModuleFromResource(conn, "lz4_xxhash", "/lz4_xxhash.js")
        installModuleFromResource(conn, "lz4", "/lz4.js")
        executeSqlFromResource(conn, "/lz4.sql")
        // Note: We know, that we do not need the replacements and code is faster without them!
        val replacements = mapOf("version" to version.toString(), "schema" to schema, "storage_id" to storageId)
        // Note: The compiler embeds the JBON classes into plv8.
        //       Therefore, we must not have it standalone, because otherwise we
        //       have two distinct instances in memory.
        //       A side effect sadly is that you need to require naksha, before you can require jbon!
        // TODO: Extend the commonjs2 code so that it allows to declare that one module contains another!
        installModuleFromResource(
            conn, "naksha", "/here-naksha-lib-plv8.js",
            beautify = true,
            replacements = replacements,
            extraCode = """
plv8.moduleCache["base"] = module.exports["here-naksha-lib-base"];
plv8.moduleCache["jbon"] = module.exports["here-naksha-lib-jbon"];
module.exports = module.exports["here-naksha-lib-plv8"];
"""
        )
        executeSqlFromResource(conn, "/naksha.sql", replacements)
        executeSqlFromResource(conn, "/jbon.sql")
        Static.createBaseInternalsIfNotExists(conn, schema, schemaOid)
        createInternalsIfNotExists()
        conn.commit()
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