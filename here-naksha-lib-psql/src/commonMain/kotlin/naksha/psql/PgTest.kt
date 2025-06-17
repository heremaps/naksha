@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

// TODO: We need to move this class into tests, but currently this class is cross-used from lib-view!

/**
 * General test setup.
 * @since 3.0.0
 */
@JsExport
class PgTest {
    companion object PgTest_C {
        /**
         * The [PlatformType] of [PgTest].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgTest::class).withPackageName(PACKAGE_NAME)

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
         * The default test map-id (schema) to use for testing _(`naksha_psql_test`)_.
         */
        @Deprecated(
            message = "Use 'map.id' of PgTestBase",
            replaceWith = ReplaceWith("map.id"),
            level = DeprecationLevel.WARNING
        )
        const val TEST_MAP_ID = "naksha_psql_test"
    }
}