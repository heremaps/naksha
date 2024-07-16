package naksha.psql

import naksha.base.*
import naksha.model.Row
import kotlin.js.JsExport

/**
 * A row as stored by Naksha in PostgresQL database.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgRow : ObjectProxy() {
    companion object {
        private val INT64 = NullableProperty<Any, PgRow, Int64>(Int64::class)
        private val INT = NullableProperty<Any, PgRow, Int>(Int::class)
        private val STRING = NullableProperty<Any, PgRow, String>(String::class)
        private val BYTE_ARRAY = NullableProperty<Any, PgRow, ByteArray>(ByteArray::class)
    }

    var created_at by INT64
    var updated_at by INT64
    var author_ts by INT64
    var txn_next by INT64
    var txn by INT64
    var ptxn by INT64
    var uid by INT
    var puid by INT
    var fnva1 by INT
    var version by INT
    var geo_grid by INT
    var flags by INT
    var origin by STRING
    var app_id by STRING
    var author by STRING
    var type by STRING
    var id by STRING
    var feature by BYTE_ARRAY
    var tags by BYTE_ARRAY
    var geo by BYTE_ARRAY
    var geo_ref by BYTE_ARRAY
}