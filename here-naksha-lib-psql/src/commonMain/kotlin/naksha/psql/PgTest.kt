@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.psql.PgPlatform.PgPlatformCompanion.getTestStorage
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@JsExport
class PgTest {
    companion object PgTest_C {
        /**
         * The default storage identifier for testing.
         */
        @JvmField
        @JsStatic
        val TEST_STORAGE_ID = "naksha_psql_test"

        /**
         * The default application name to use for testing.
         */
        @JvmField
        @JsStatic
        val TEST_APP_NAME: String = "naksha.psql.testApp"

        /**
         * The default application identifier to use for testing.
         */
        @JvmField
        @JsStatic
        val TEST_APP_ID: String = "naksha.psql.testAppId"

        /**
         * The default author to use for testing.
         */
        @JvmField
        @JsStatic
        val TEST_APP_AUTHOR: String? = "naksha.psql.testAuthor"

        /**
         * The default schema to use for testing.
         */
        @JvmField
        @JsStatic
        val TEST_SCHEMA: String = "naksha_psql_test"
    }
}