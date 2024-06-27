package naksha.plv8

expect class PgUtil {
    companion object {
        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts The literal parts to merge and quote.
         * @return The quoted literal.
         */
        fun quoteLiteral(vararg parts: String): String

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        fun quoteIdent(vararg parts: String): String
    }
    
}