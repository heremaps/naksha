package naksha.psql

import kotlinx.js.JsPlainObject

/**
 * Expose the native PLV8 api.
 *
 * - The logger API is already exposed through [naksha.base.Platform.logger]
 * - The `quote_literal` and `quote_ident` methods are as well exposed through [PgPlatform.quote_literal] and [PgPlatform.quote_ident].
 * - See [PLV8 documentation](https://plv8.github.io/)
 */
external interface Plv8 {
    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
     * @param string the string to quote.
     * @return the quoted literal.
     */
    fun quote_literal(string: String): String

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
     * @param string the string to quote.
     * @return the quoted ident.
     */
    fun quote_ident(string: String): String
}

/**
 * Returns the global `plv8` object.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun plv8() : Plv8 = js("plv8").unsafeCast<Plv8>()
