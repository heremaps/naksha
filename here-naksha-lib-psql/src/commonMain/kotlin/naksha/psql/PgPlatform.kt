package naksha.psql

/**
 * PostgresQL utility and factory functions. They are implemented differently on every platform.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PgPlatform {
    companion object PgPlatformCompanion {
        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return the quoted literal, _null_ if there is no platform specific implementation (fallback to default implementation).
         */
        internal fun quote_literal(vararg parts: String): String?

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         * @return the quoted identifier, _null_ if there is no platform specific implementation (fallback to default implementation).
         */
        internal fun quote_ident(vararg parts: String): String?

        /**
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions, and the rest addresses the partition, for example for 4 partitions do `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         *
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
        fun partitionNumber(featureId: String): Int

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        fun isPlv8(): Boolean
    }
}
