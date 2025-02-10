@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * General test setup.
 * @since 3.0.0
 */
@JsExport
class PgTest {
    companion object PgTest_C {
        /**
         * The default storage identifier for testing.
         */
        const val TEST_STORAGE_ID = "naksha_psql_test"

        /**
         * The default application name to use for testing.
         */
        const val TEST_APP_NAME = "naksha.psql.testApp"

        /**
         * The default application identifier to use for testing.
         */
        const val TEST_APP_ID = "naksha.psql.testAppId"

        /**
         * The default author to use for testing.
         */
        const val TEST_APP_AUTHOR = "naksha.psql.testAuthor"

        /**
         * The default test map-id (schema) to use for testing.
         */
        const val TEST_MAP_ID = "naksha_psql_test"
    }
}