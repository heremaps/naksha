@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import naksha.base.P_JsMap
import naksha.base.PlatformMap
import naksha.psql.IPlv8Sql
import naksha.psql.Param
import naksha.psql.PgDbInfo

/**
 * Thin wrapper around the native plv8 engine methods. This wrapper allows to simulate this in the JVM.
 */
@Suppress("UnsafeCastFromDynamic")
@JsExport
class JsPlv8Sql : IPlv8Sql {
    private val dbInfo = PgDbInfo(this)

    override fun info(): PgDbInfo = dbInfo

    override fun quoteLiteral(vararg parts: String): String {
        return js("plv8.quote_literal(parts.join(\"\"))")
    }

    override fun quoteIdent(vararg parts: String): String {
        return js("plv8.quote_ident(parts.join(\"\"))")
    }

    override fun affectedRows(any: Any): Int? {
        return js("typeof any === \"number\" && any || null")
    }

    override fun rows(any: Any): Array<P_JsMap>? {
        return (js("Array.isArray(any) && any || null") as Array<Any>)
            .map { (it as PlatformMap).proxy(P_JsMap::class) }
            .toTypedArray()
    }

    override fun execute(sql: String, args: Array<Any?>?): Any {
        return js("args ? plv8.execute(sql, args) : plv8.execute(sql)")
    }

    override fun prepare(sql: String, typeNames: Array<String>?): naksha.psql.IPlv8Plan {
        return js("typeNames ? plv8.prepare(sql, typeNames) : plv8.execute(typeNames)")
    }

    override fun executeBatch(plan: naksha.psql.IPlv8Plan, bulkParams: Array<Array<Param>>): IntArray {
        for (singleQueryParams in bulkParams) {
            val executionParams = singleQueryParams.map { it.value }.toTypedArray()
            js("plan.execute(executionParams)")
        }
        return intArrayOf(bulkParams.size)
    }

    override fun gzipCompress(raw: ByteArray): ByteArray {
        return rows(execute("SELECT gzip($1) as compressed", arrayOf(raw)))!![0]["compressed"] as ByteArray
    }

    override fun gzipDecompress(raw: ByteArray): ByteArray {
        return rows(execute("SELECT gunzip($1) as uncompressed", arrayOf(raw)))!![0]["uncompressed"] as ByteArray
    }
}