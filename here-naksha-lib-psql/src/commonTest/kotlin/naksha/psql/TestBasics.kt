package naksha.psql

import naksha.model.NakshaContext
import kotlin.test.Test

/**
 * Abstract class for all tests using connection to db.
 * @property dropSchema if the schema should be dropped before running the test.
 * @property initStorage if the storage should be initialized before running the test.
 * @property run if the test should run.
 */
@Suppress("MemberVisibilityCanBePrivate")

abstract class TestBasics(val dropSchema: Boolean = true, val initStorage: Boolean = true, val run: Boolean = true) {

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

    @Test
    fun t001_drop_schema_if_exists() {
        if (run && dropSchema) nakshaSession.usePgConnection()
            .execute("DROP SCHEMA IF EXISTS ${PgUtil.quoteIdent(nakshaSession.options.schema)} CASCADE")
    }

    @Test
    fun t002_init_storage() {
        if (run && initStorage) storage.initStorage(mapOf(PgUtil.ID to STORAGE_ID, PgUtil.CONTEXT to testContext))
    }
}