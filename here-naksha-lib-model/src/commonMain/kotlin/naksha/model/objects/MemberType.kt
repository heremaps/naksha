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
 *   same flat key/value representation as [TAGS].
 *   This exists for downward compatibility with XYZ Hub and previous Naksha v2 clients that send
 *   tags as arrays rather than maps.
 *   Only valid as a [Member] type; not a valid [IndexType].
 *   To index a [TAGS_FROM_ARRAY] column use [IndexType.TAGS].
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
    }
}
