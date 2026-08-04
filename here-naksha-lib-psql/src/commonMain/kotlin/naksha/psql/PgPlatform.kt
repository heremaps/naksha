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
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        fun isPlv8(): Boolean
    }
}
