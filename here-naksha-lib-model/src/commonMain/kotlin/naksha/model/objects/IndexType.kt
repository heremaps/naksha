@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The kind of index to create for an [Index].
 *
 * - [BTREE] — ordered index for equality and range queries on primitive columns (numbers, booleans, strings, byte-arrays).
 * - [SPATIAL] — spatial index over a geometry column ([MemberType.SPATIAL]) (e.g. the built-in `geo`).
 * - [TAGS] — inverted index over a tags column ([MemberType.TAGS] or [MemberType.TAGS_FROM_ARRAY]);
 *   supports key/value containment lookups.
 * - [SET] — inverted index over a set column ([MemberType.SET]); supports element containment lookups.
 * @since 3.0
 */
@JsExport
class IndexType : JsEnum() {

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = IndexType::class

    override fun initClass() {}

    companion object IndexType_C {
        /**
         * Ordered index for equality and range queries on primitive columns.
         * @since 3.0
         */
        @JvmField
        val BTREE = defIgnoreCase(IndexType::class, "btree")

        /**
         * Spatial index over a geometry column.
         * @since 3.0
         */
        @JvmField
        val SPATIAL = defIgnoreCase(IndexType::class, "spatial")

        /**
         * Inverted index over a [MemberType.TAGS] or [MemberType.TAGS_FROM_ARRAY] column.
         * Supports key/value containment lookups.
         * @since 3.0
         */
        @JvmField
        val TAGS = defIgnoreCase(IndexType::class, "tags")

        /**
         * Inverted index over a [MemberType.SET] column. Supports element containment lookups,
         * e.g. find all features whose tags set contains the element `"foo"`. This is the default
         * index for the standard `tags` member.
         * @since 3.0
         */
        @JvmField
        val SET = defIgnoreCase(IndexType::class, "set")
    }
}
