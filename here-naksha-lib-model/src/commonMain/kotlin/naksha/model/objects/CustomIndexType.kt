@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The kind of index to create for a [CustomIndex].
 *
 * - [BTREE] — ordered index for equality and range queries on primitive columns (numbers, booleans, strings, byte-arrays).
 * - [SPATIAL] — spatial index over a geometry column (e.g. the built-in `geo`).
 * - [FLAT_MAP] — inverted index over a column of [CustomMemberType.FLAT_MAP] or [CustomMemberType.TAGS]; supports key/value containment lookups.
 * @since 3.0
 */
@JsExport
class CustomIndexType : JsEnum() {

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = CustomIndexType::class

    override fun initClass() {}

    companion object CustomIndexType_C {
        /**
         * Ordered index for equality and range queries on primitive columns.
         * @since 3.0
         */
        @JvmField
        val BTREE = defIgnoreCase(CustomIndexType::class, "btree")

        /**
         * Spatial index over a geometry column.
         * @since 3.0
         */
        @JvmField
        val SPATIAL = defIgnoreCase(CustomIndexType::class, "spatial")

        /**
         * Inverted index over a [CustomMemberType.FLAT_MAP] or [CustomMemberType.TAGS] column.
         * @since 3.0
         */
        @JvmField
        val FLAT_MAP = defIgnoreCase(CustomIndexType::class, "flat_map")
    }
}
