@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha
import kotlin.js.JsExport

/**
 * The internal collections table.
 */
@JsExport
class PgNakshaCollections internal constructor(schema: PgMap) : PgCollection(schema, Naksha.COLLECTIONS_COL), PgInternalCollection
