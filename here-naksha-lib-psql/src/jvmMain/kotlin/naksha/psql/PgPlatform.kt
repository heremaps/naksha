package naksha.psql

import naksha.model.*
import java.security.MessageDigest

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PgPlatform {
    actual companion object PgPlatformCompanion {

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect the test storage against an
         * existing database with this URL.
         *
         * If not given, this parameter is auto-detected from the environment variable named `NAKSHA_TEST_PSQL_DB_URL`.
         *
         * @see PgTest
         */
        const val TEST_URL = "test_url"

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect against the given instance. The value must be a [PgInstance].
         *
         * @see PgTest
         */
        const val TEST_INSTANCE = "test_instance"

        @JvmStatic
        internal actual fun quote_literal(vararg parts: String): String? = null

        @JvmStatic
        internal actual fun quote_ident(vararg parts: String): String? = null

        @JvmStatic
        private val md5Digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JvmStatic
        actual fun isPlv8(): Boolean = false
    }
}