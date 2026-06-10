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
 * - [SPATIAL] — spatial index over a geometry column (e.g. the built-in `geo`).
 * - [SET] — inverted index over a [MemberType.SET] column (a `jsonb` **array** of primitives);
 *   supports element-exists / element-containment lookups (`?`, `?|`, `?&`).
 * - [TAGS] — inverted index over a tags column (a `jsonb` **object**; [MemberType.TAGS] or
 *   [MemberType.TAGS_FROM_ARRAY]); supports key/value containment lookups.
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
         * Inverted index over a [MemberType.SET] column (`jsonb` array of primitives).
         * Supports element-exists / element-containment lookups (`?`, `?|`, `?&`).
         * @since 3.0
         */
        @JvmField
        val SET = defIgnoreCase(IndexType::class, "set")

        /**
         * Inverted index over a [MemberType.TAGS] or [MemberType.TAGS_FROM_ARRAY] column
         * (`jsonb` object of primitives). Supports key/value containment lookups.
         * @since 3.0
         */
        @JvmField
        val TAGS = defIgnoreCase(IndexType::class, "tags")
    }
}
