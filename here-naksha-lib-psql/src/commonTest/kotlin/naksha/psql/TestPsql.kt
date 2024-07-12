package naksha.psql

import kotlin.test.Test

/**
 * Test the basics of the database, which is creation of schema,
 */
class TestPsql : TestBasics() {

    @Test
    fun test_feature_insertion() {
        drop_schema()
        init_storage()
        pgConnection.commit()
    }
}