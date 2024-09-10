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

        val naksha_collections = schema[Naksha.VIRT_COLLECTIONS]
        assertTrue(naksha_collections.exists(), "${Naksha.VIRT_COLLECTIONS} should exist!")
        val naksha_dictionaries = schema[Naksha.VIRT_DICTIONARIES]
        assertTrue(naksha_dictionaries.exists(), "${Naksha.VIRT_DICTIONARIES} should exist!")
        val naksha_transactions = schema[Naksha.VIRT_TRANSACTIONS]
        assertTrue(naksha_transactions.exists(), "${Naksha.VIRT_TRANSACTIONS} should exist!")
    }
}