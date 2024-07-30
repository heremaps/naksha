@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaUtil
import kotlin.js.JsExport

/**
 * The internal collections table.
 */
@JsExport
class PgNakshaCollections internal constructor(schema: PgSchema) : PgCollection(schema, NakshaUtil.VIRT_COLLECTIONS), PgInternalCollection
