@file:Suppress("OPT_IN_USAGE")

package naksha.psql.base

import naksha.base.PlatformUtil
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.psql.*
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Abstract class for all tests using connection to db.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
class TestEnv(
    dropSchema: Boolean,
    enableInfoLogs: Boolean = false,
    @JvmField val mapId: String = PgTest.TEST_MAP_ID
) {
    init {
        PlatformUtil.ENABLE_INFO = enableInfoLogs
        NakshaContext.defaultMapId.set(mapId)
        NakshaContext.currentContext().mapId = mapId
    }

    /**
     * The test local storage using [Naksha.LOCAL_TEST_STORAGE_CONFIG].
     */
    @JvmField
    val storage = Naksha.useStorage(Naksha.LOCAL_TEST_STORAGE_CONFIG) as PgStorage

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
                c = pgSession.useConnection()
                _pgConnection = c
            }
            return c
        }

    fun dropSchema() {
        val conn = storage.newConnection(options, false) { _, _ -> }
        conn.use {
            conn.execute("""DROP SCHEMA IF EXISTS ${quoteIdent(mapId)} CASCADE;
DROP SCHEMA IF EXISTS ${quoteIdent(Naksha.ADMIN_MAP)} CASCADE;""").close()
            conn.commit()
        }
    }

    init {
        if (dropSchema) dropSchema()
        context.attachToCurrentThread()
    }
}