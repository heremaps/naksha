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
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the
         * given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions and the rest
         * addresses the partition, for example for 4 partitions we get `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         * In PVL8 this is implemented using the native code as `get_byte(digest(id,'md5'),0)`, which is as well what the partitioning
         * statement will do.
         * @param featureId the feature id.
         * @return the partition number of the feature, a value between 0 and 255.
         */
        @Deprecated(
            message = "This function will be removed in a future release.",
            replaceWith = ReplaceWith("Naksha.partitionNumber(Naksha.featureNumber(featureId))"),
            level = DeprecationLevel.WARNING
        )
        @JvmStatic
        actual fun partitionNumber(featureId: String): Int = Naksha.partitionNumber(Naksha.featureNumber(featureId))

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JvmStatic
        actual fun isPlv8(): Boolean = false
    }
}