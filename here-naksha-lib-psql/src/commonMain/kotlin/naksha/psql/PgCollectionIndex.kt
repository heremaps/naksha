package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
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
) {
    companion object PgCollectionIndex_C {
        /**
         * The [PlatformType] of [PgCollectionIndex].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgCollectionIndex::class).withPackageName(PACKAGE_NAME)
    }
}