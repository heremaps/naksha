@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * All internal collections extend this class.
 */
@JsExport
abstract class PgInternalCollection internal constructor(schema: PgSchema, id: String) : PgCollection(schema, id)
