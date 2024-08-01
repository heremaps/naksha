package naksha.psql

import naksha.base.PlatformUtil
import naksha.model.NakshaContext
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

/**
 * Abstract class for all tests using connection to db.
 */
@Suppress("MemberVisibilityCanBePrivate")
class TestEnv(dropSchema: Boolean, initStorage: Boolean, enableInfoLogs:Boolean = false) {
    init {
        PlatformUtil.ENABLE_INFO = enableInfoLogs
    }

    companion object TestBasicsCompanion {
        const val STORAGE_ID = "naksha_psql_test"
    }

    val storage = PgPlatform.newTestStorage()

    /**
     * The default [NakshaContext] to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or
     * [PgStorage.newReadSession].
     */
    val context = NakshaContext.newInstance(storage.defaultOptions.appId, storage.defaultOptions.author, su = true)
    val options = storage.defaultOptions.copy(readOnly = false, useMaster = true)
    private var _pgSession: PgSession? = null

    /**
     * The PostgresQL session to be used to testing, late initialized to capture errors.
     */
    val pgSession: PgSession
        get() {
            var s = _pgSession
            if (s == null) {
                s = storage.newSession(options)
                _pgSession = s
            }
            return s
        }

    private var _pgConnection: PgConnection? = null
    val pgConnection: PgConnection
        get() {
            var c = _pgConnection
            if (c == null) {
                c = pgSession.usePgConnection()
                _pgConnection = c
            }
            return c
        }

    fun dropSchema() {
        val conn = storage.newConnection(options) { _, _ -> }
        conn.use {
            conn.execute("DROP SCHEMA IF EXISTS ${quoteIdent(options.schema)} CASCADE").close()
            conn.commit()
        }
    }

    fun initStorage() {
        storage.initStorage(mapOf(PgUtil.ID to STORAGE_ID, PgUtil.CONTEXT to context))
    }

    init {
        if (dropSchema) dropSchema()
        if (initStorage) initStorage()
        context.attachToCurrentThread()
    }
}