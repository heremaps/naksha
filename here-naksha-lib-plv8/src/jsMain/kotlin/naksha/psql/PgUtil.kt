package naksha.psql

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PgUtil {
    @Suppress("OPT_IN_USAGE")
    actual companion object {
        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts The literal parts to merge and quote.
         * @return The quoted literal.
         */
        @JsStatic
        actual fun quoteLiteral(vararg parts: String): String {
            // Use plv8.quote_literal
            TODO("Not yet implemented")
        }

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        @JsStatic
        actual fun quoteIdent(vararg parts: String): String {
            // Use plv8.quote_ident
            TODO("Not yet implemented")
        }
    }
}