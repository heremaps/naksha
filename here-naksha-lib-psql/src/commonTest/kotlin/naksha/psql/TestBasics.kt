package naksha.psql

import naksha.model.NakshaContext
import naksha.psql.PgUtil.Companion.quoteIdent

/**
 * Abstract class for all tests using connection to db.
 */
@Suppress("MemberVisibilityCanBePrivate")

abstract class TestBasics {

    companion object {
        val storage = PgUtil.getTestStorage()
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

        const val STORAGE_ID = "naksha_psql_test"
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

    fun drop_schema() {
        val conn = storage.newConnection(options) { _, _ -> }
        conn.use {
            conn.execute("DROP SCHEMA IF EXISTS ${quoteIdent(options.schema)} CASCADE").close()
            conn.commit()
        }
    }

    fun init_storage() {
        storage.initStorage(mapOf(PgUtil.ID to STORAGE_ID, PgUtil.CONTEXT to context))
    }
}