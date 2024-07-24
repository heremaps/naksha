package naksha.psql

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Index information for a collection.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgCollectionIndex(
    @JvmField val collection: PgCollection,
    @JvmField val index: PgIndex,
    @JvmField val onHead: Boolean = true,
    @JvmField val onDelete: Boolean = true,
    @JvmField val onHistory: Boolean = true,
    @JvmField val onMeta: Boolean = true
)