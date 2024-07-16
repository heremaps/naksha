@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * The internal collections table.
 */
@JsExport
class NakshaCollections internal constructor(schema: PgSchema) : PgInternalCollection(schema, ID) {
    companion object NakshaCollectionsCompanion {
        const val ID = "naksha~collections"
    }
}
