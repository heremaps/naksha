package naksha.psql

import naksha.model.NakshaContext
import naksha.psql.PgUtil.Companion.quoteIdent

/**
 * Abstract class for all tests using connection to db.
 */
@Suppress("MemberVisibilityCanBePrivate")

abstract class TestBasics {

    companion object {
        /**
         * The default [NakshaContext] to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or
         * [PgStorage.newReadSession].
         */
        val testContext = NakshaContext.newInstance("naksha.psql.testAppId", "naksha.psql.TestUser", su = true)

        val storage = PgUtil.getTestStorage()
        private var _nakshaSession: NakshaSession? = null

        /**
         * The PostgresQL session to be used to testing, late initialized to capture errors.
         */
        val nakshaSession: NakshaSession
            get() {
                var s = _nakshaSession
                if (s == null) {
                    s = storage.newNakshaSession(testContext, storage.defaultOptions.copy(readOnly = false, useMaster = true))
                    _nakshaSession = s
                }
                return s
            }

        const val STORAGE_ID = "naksha_psql_test"
    }

    fun drop_schema() {
        nakshaSession.usePgConnection().execute("DROP SCHEMA IF EXISTS ${quoteIdent(nakshaSession.options.schema)} CASCADE").close()
    }

    fun init_storage() {
        storage.initStorage(mapOf(PgUtil.ID to STORAGE_ID, PgUtil.CONTEXT to testContext))
    }
}