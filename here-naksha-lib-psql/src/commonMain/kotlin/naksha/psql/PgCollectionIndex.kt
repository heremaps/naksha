package naksha.psql

import kotlin.js.JsExport

/**
 * Index information for a collection.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgCollectionIndex(
    val collection: PgCollection,
    val index: PgIndex,
    val onHead: Boolean = true,
    val onDelete: Boolean = true,
    val onHistory: Boolean = true,
    val onMeta: Boolean = true
)