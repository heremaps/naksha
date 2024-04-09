@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

/**
 * Thin wrapper around the native plv8 engine methods. This wrapper allows to simulate this in the JVM.
 */
@Suppress("UnsafeCastFromDynamic")
@JsExport
class JsPlv8Sql : IPlv8Sql {
    private val dbInfo = PgDbInfo(this)

    override fun info(): PgDbInfo = dbInfo

    override fun newTable(): ITable {
        return JsPlv8Table()
    }

    override fun quoteLiteral(vararg parts: String): String {
        return js("plv8.quote_literal(parts.join(\"\"))")
    }

    override fun quoteIdent(vararg parts: String): String {
        return js("plv8.quote_ident(parts.join(\"\"))")
    }

    override fun affectedRows(any: Any): Int? {
        return js("typeof any === \"number\" && any || null")
    }

    override fun rows(any: Any): Array<Any>? {
        return js("Array.isArray(any) && any || null")
    }

    override fun execute(sql: String, args: Array<Any?>?): Any {
        return js("args ? plv8.execute(sql, args) : plv8.execute(sql)")
    }

    override fun prepare(sql: String, typeNames: Array<String>?): IPlv8Plan {
        return js("typeNames ? plv8.prepare(sql, typeNames) : plv8.execute(typeNames)")
    }
}