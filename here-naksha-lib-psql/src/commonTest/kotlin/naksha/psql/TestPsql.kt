package naksha.psql

import kotlin.test.Test

/**
 * Test the basics of the database, which is creation of schema,
 */
class TestPsql {
    private val env = TestEnv(dropSchema = true, initStorage = true)

    @Test
    fun test_basics() {
    }
}