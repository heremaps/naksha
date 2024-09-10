@file:Suppress("OPT_IN_USAGE")

package naksha.psql.base

import naksha.base.PlatformUtil
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.psql.*
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport

/**
 * Abstract class for all tests using connection to db.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
class TestEnv(dropSchema: Boolean, initStorage: Boolean, enableInfoLogs: Boolean = false) {
    init {
        PlatformUtil.ENABLE_INFO = enableInfoLogs
    }

    companion object TestBasicsCompanion {
    }

    val storage = PgPlatform.newTestStorage()

    /**
     * The default [NakshaContext] to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or
     * [PgStorage.newReadSession].
     */
    val context = NakshaContext.newInstance(
        appId = PgTest.TEST_APP_ID,
        author = PgTest.TEST_APP_AUTHOR,
        su = true
    )
    val options = SessionOptions.from(context)
    private var _pgSession: PgSession? = null

    /**
     * The PostgresQL session to be used to testing, late initialized to capture errors.
     */
    val pgSession: PgSession
        get() {
            var s = _pgSession
            if (s == null) {
                s = storage.newSession(options, false)
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
        val conn = storage.newConnection(options, false) { _, _ -> }
        conn.use {
            conn.execute("DROP SCHEMA IF EXISTS ${quoteIdent(storage.defaultSchemaName)} CASCADE")
                .close()
            conn.commit()
        }
    }

    fun initStorage() {
        storage.initStorage(mapOf(PgUtil.ID to PgTest.TEST_STORAGE_ID, PgUtil.CONTEXT to context))
    }

    init {
        if (dropSchema) dropSchema()
        if (initStorage) initStorage()
        context.attachToCurrentThread()
    }
}