@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * The internal collections table.
 */
@JsExport
class NakshaCollections internal constructor(schema: PgSchema) : PgCollection(schema, ID), PgInternalCollection {
    companion object NakshaCollectionsCompanion {
        const val ID = "naksha~collections"
    }
}
