package naksha.psql

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")
@JsExport
actual class PgPlatform {
    actual companion object PgPlatformCompanion {
        private fun plv8Forbidden(opName: String) {
            if (isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in PLV8 storage")
        }

        private fun browserForbidden(opName: String) {
            if (!isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in the browser")
        }

        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         */
        internal actual fun quote_literal(vararg parts: String): String?
            = if (isPlv8()) js("""
parts && parts.length>0 ? (parts.length===1 ? plv8.quote_literal(parts[0]) : plv8.quote_literal(parts.join(''))) : ''
""").unsafeCast<String>() else null

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        internal actual fun quote_ident(vararg parts: String): String?
                = if (isPlv8()) js("""
parts && parts.length>0 ? (parts.length===1 ? plv8.quote_ident(parts[0]) : plv8.quote_literal(parts.join(''))) : ''
""").unsafeCast<String>() else null

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JsStatic
        actual fun isPlv8(): Boolean = js("typeof plv8==='object'").unsafeCast<Boolean>()
    }
}