@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The logical data type of a [Member].
 *
 * - Primitives: [BOOLEAN], [INT8], [INT16], [INT32], [INT64], [FLOAT32], [FLOAT64], [STRING], [BYTE_ARRAY].
 * - [SPATIAL]: a geometry stored as raw TWKB bytes. The storage persists this as a binary column and
 *   supports spatial queries. Only a [IndexType.SPATIAL] index may be placed on a [SPATIAL] member.
 * - [TAGS]: a map whose keys are strings and values are primitives (matches JBON2 tag-map specification).
 *   The storage persists this as a flat key/value map that supports containment queries.
 * - [TAGS_FROM_ARRAY]: like [TAGS] but the input is a `TagList` (Naksha tag-array syntax, e.g.
 *   `["key=value", "name:=42"]`). The list is converted to a tag-map at write time and stored in the
 *   same flat key/value representation as [TAGS]. Beware that the conversion has a side effect:
 *   reading the feature back returns the tags re-flattened from the map, so the original array
 *   order is **not** preserved.
 *   Only valid as a [Member] type; not a valid [IndexType].
 *   To index a [TAGS_FROM_ARRAY] column use [IndexType.TAGS].
 * - [SET]: a JSON array of unique primitive values (booleans, numbers, strings). The array is stored
 *   unmodified, so the element order is preserved when reading the feature back. Supports
 *   element-containment queries via [IndexType.SET]. This is the default type of the standard
 *   `tags` member, which keeps 100% downward compatibility with the classic XYZ tags array at
 *   `properties -> @ns:com:here:xyz -> tags`.
 * @since 3.0
 */
@JsExport
class MemberType : JsEnum() {

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = MemberType::class

    override fun initClass() {}

    companion object MemberType_C {
        /**
         * Boolean.
         * @since 3.0
         */
        @JvmField
        val BOOLEAN = defIgnoreCase(MemberType::class, "boolean")

        /**
         * 8-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT8 = defIgnoreCase(MemberType::class, "int8")

        /**
         * 16-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT16 = defIgnoreCase(MemberType::class, "int16")

        /**
         * 32-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT32 = defIgnoreCase(MemberType::class, "int32")

        /**
         * 64-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT64 = defIgnoreCase(MemberType::class, "int64")

        /**
         * 32-bit IEEE-754 floating point in storage, but when reading from book, decode as double.
         * @since 3.0
         */
        @JvmField
        val FLOAT32 = defIgnoreCase(MemberType::class, "float32")

        /**
         * 64-bit IEEE-754 floating point.
         * @since 3.0
         */
        @JvmField
        val FLOAT64 = defIgnoreCase(MemberType::class, "float64")

        /**
         * Variable-length string.
         * @since 3.0
         */
        @JvmField
        val STRING = defIgnoreCase(MemberType::class, "string")

        /**
         * Raw byte array.
         * @since 3.0
         */
        @JvmField
        val BYTE_ARRAY = defIgnoreCase(MemberType::class, "byte_array")

        /**
         * A tuple-number, can be encoded as string or byte-array _(storage decides)_. To be used with [IndexType.BTREE].
         * @since 3.0
         */
        @JvmField
        val TUPLE_NUMBER = defIgnoreCase(MemberType::class, "tuple_number")

        /**
         * A geometry stored as raw [TWKB](https://github.com/nicowillis/twkb) bytes.
         *
         * The storage layer (`lib-psql`) persists this as a `bytea` column with `STORAGE EXTERNAL`
         * and interprets it as TWKB-encoded geometry for spatial indexing and queries.
         *
         * Only [IndexType.SPATIAL] may be used with a `SPATIAL` member.
         * @since 3.0
         */
        @JvmField
        val SPATIAL = defIgnoreCase(MemberType::class, "spatial")

        /**
         * A map whose keys are strings and values are primitives, following the JBON2 tag-map
         * specification. The storage persists this as a flat key/value map that supports
         * containment queries.
         *
         * Indexed via [IndexType.TAGS].
         * @since 3.0
         */
        @JvmField
        val TAGS = defIgnoreCase(MemberType::class, "tags")

        /**
         * A string-array using Naksha tag syntax that is expanded into a [TAGS] map at write time.
         * Provided for downward compatibility with XYZ Hub and previous Naksha v2 clients that send
         * tags as arrays rather than maps.
         *
         * Input is `["key=value", "name:=42"]`; materialized form is `{"key":"value","name":42}`.
         * Stored in the same flat key/value representation as [TAGS].
         *
         * **Not valid as an [IndexType].** To index this column use [IndexType.TAGS].
         * @since 3.0
         */
        @JvmField
        val TAGS_FROM_ARRAY = defIgnoreCase(MemberType::class, "tags_from_array")

        /**
         * A JSON array of unique primitive values (booleans, numbers, strings), following the JBON2
         * set specification: entries must not be `null` or duplicates, and the order is significant.
         * The storage persists the array unmodified (as a JSON array in `jsonb`), so the element
         * order is guaranteed to be preserved when reading the feature back.
         *
         * This is the default type of the standard `tags` member (the classic XYZ tags array at
         * `properties -> @ns:com:here:xyz -> tags`, e.g. `["foo", "bar"]`). In contrast to
         * [TAGS_FROM_ARRAY] the values are not split into key/value pairs, therefore only full
         * elements can be searched, not keys or values.
         *
         * Indexed via [IndexType.SET].
         * @since 3.0
         */
        @JvmField
        val SET = defIgnoreCase(MemberType::class, "set")
    }
}
