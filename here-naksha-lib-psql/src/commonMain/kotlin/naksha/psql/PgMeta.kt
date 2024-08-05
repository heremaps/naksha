@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A META table.
 */
@JsExport
class PgMeta(val head: PgHead) : PgTable(head.collection, "${head.collection.id}${PG_META}", head.storageClass, true)
