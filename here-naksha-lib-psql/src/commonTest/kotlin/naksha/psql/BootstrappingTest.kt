package naksha.psql

import naksha.psql.base.PgTestBase
import kotlin.test.Test

/**
 * Tests ensuring correct bootstrapping
 */
class BootstrappingTest: PgTestBase() {

    @Test
    fun shouldCreateProperSchema() {
        // TODO: We need to perform SQL queries to ensure that "naksha~admin" has been created, and all the other tables!
//        val schema = env.storage.defaultMap
//        assertTrue(schema.exists(), "The default schema should exists!")
//        val naksha_collections = schema[Naksha.COLLECTIONS_COL]
//        assertTrue(naksha_collections.exists(), "${Naksha.COLLECTIONS_COL} should exist!")
//        val naksha_dictionaries = schema[Naksha.DICTIONARIES_COL]
//        assertTrue(naksha_dictionaries.exists(), "${Naksha.DICTIONARIES_COL} should exist!")
//        val naksha_transactions = schema[Naksha.TRANSACTIONS_COL]
//        assertTrue(naksha_transactions.exists(), "${Naksha.TRANSACTIONS_COL} should exist!")
    }
}