package naksha.psql

import naksha.base.*
import kotlin.js.JsExport

/**
 * A row as stored by Naksha in PostgresQL database.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class NakshaRow : ObjectProxy() {
    companion object {
        private val INT64 = NullableProperty<Any, NakshaRow, Int64>(Int64::class)
        private val INT = NullableProperty<Any, NakshaRow, Int>(Int::class)
        private val STRING = NullableProperty<Any, NakshaRow, String>(String::class)
        private val BYTE_ARRAY = NullableProperty<Any, NakshaRow, ByteArray>(ByteArray::class)
    }

    val created_at by INT64
    val updated_at by INT64
    val author_ts by INT64
    val txn_next by INT64
    val txn by INT64
    val ptxn by INT64
    val uid by INT
    val puid by INT
    val fnva1 by INT
    val version by INT
    val geo_grid by INT
    val flags by INT
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