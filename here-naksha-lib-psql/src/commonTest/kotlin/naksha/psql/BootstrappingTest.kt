package naksha.psql

import naksha.model.Naksha
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests ensuring correct bootstrapping
 */
class BootstrappingTest: PgTestBase() {

    @Test
    fun shouldCreateProperSchema() {
        val schema = env.storage.defaultMap
        assertTrue(schema.exists(), "The default schema should exists!")

        val naksha_collections = schema[Naksha.COLLECTIONS_COL]
        assertTrue(naksha_collections.exists(), "${Naksha.COLLECTIONS_COL} should exist!")
        val naksha_dictionaries = schema[Naksha.ADMIN_DICT_COL]
        assertTrue(naksha_dictionaries.exists(), "${Naksha.ADMIN_DICT_COL} should exist!")
        val naksha_transactions = schema[Naksha.ADMIN_TRANSACTIONS_COL]
        assertTrue(naksha_transactions.exists(), "${Naksha.ADMIN_TRANSACTIONS_COL} should exist!")
    }
}