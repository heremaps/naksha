@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.JsEnum
import naksha.geo.SpGeometry
import naksha.model.NakshaError.NakshaErrorCompanion.INITIALIZATION_FAILED
import naksha.model.NakshaException
import naksha.model.TagMap
import naksha.model.TupleNumber
import naksha.model.objects.BoolMember
import naksha.model.objects.ByteArrayMember
import naksha.model.objects.Float32Member
import naksha.model.objects.Float64Member
import naksha.model.objects.Int16Member
import naksha.model.objects.Int32Member
import naksha.model.objects.Int64Member
import naksha.model.objects.Int8Member
import naksha.model.objects.SpatialMember
import naksha.model.objects.TagListMember
import naksha.model.objects.StringMember
import naksha.model.objects.TagsMember
import naksha.model.objects.TupleNumberMember
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
 * - [TAG_LIST]: a list of unique primitive values (booleans, numbers, strings). Value order is
 *   significant, but must not have duplicates, null or undefined. The storage persists the list
 *   unmodified, so the element order is guaranteed to be preserved when reading the feature back.
 *   Supports element-containment queries via [IndexType.TAG_LIST]. This is the default type of the
 *   standard `tags` member, which keeps 100% downward compatibility with the classic XYZ tags array
 *   at `properties -> @ns:com:here:xyz -> tags`.
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
        val BOOLEAN = defIgnoreCase(MemberType::class, "boolean") { self -> self.sortOrder = 6; self.subtype = BoolMember::class }

        /**
         * 8-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT8 = defIgnoreCase(MemberType::class, "int8") { self -> self.sortOrder = 5; self.subtype = Int8Member::class }

        /**
         * 16-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT16 = defIgnoreCase(MemberType::class, "int16") { self -> self.sortOrder = 4; self.subtype = Int16Member::class }

        /**
         * 32-bit signed integer in storage, but when reading from book, decode as long.
         * @since 3.0
         */
        @JvmField
        val INT32 = defIgnoreCase(MemberType::class, "int32") { self -> self.sortOrder = 2; self.subtype = Int32Member::class }

        /**
         * 64-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT64 = defIgnoreCase(MemberType::class, "int64") { self -> self.sortOrder = 0; self.subtype = Int64Member::class }

        /**
         * 32-bit IEEE-754 floating point in storage, but when reading from book, decode as double.
         * @since 3.0
         */
        @JvmField
        val FLOAT32 = defIgnoreCase(MemberType::class, "float32"){ self -> self.sortOrder = 3; self.subtype = Float32Member::class }

        /**
         * 64-bit IEEE-754 floating point.
         * @since 3.0
         */
        @JvmField
        val FLOAT64 = defIgnoreCase(MemberType::class, "float64") { self -> self.sortOrder = 1; self.subtype = Float64Member::class }

        /**
         * Variable-length string.
         * @since 3.0
         */
        @JvmField
        val STRING = defIgnoreCase(MemberType::class, "string") { self -> self.sortOrder = 7; self.subtype = StringMember::class }

        /**
         * Raw byte array.
         * @since 3.0
         */
        @JvmField
        val BYTE_ARRAY = defIgnoreCase(MemberType::class, "byte_array") { self -> self.sortOrder = 13; self.subtype = ByteArrayMember::class }

        /**
         * A tuple-number, can be encoded as string or byte-array _(storage decides)_. To be used with [IndexType.BTREE].
         * @since 3.0
         */
        @JvmField
        val TUPLE_NUMBER = defIgnoreCase(MemberType::class, "tuple_number") { self -> self.sortOrder = 12; self.subtype = TupleNumberMember::class }

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
        val SPATIAL = defIgnoreCase(MemberType::class, "spatial") { self -> self.sortOrder = 11; self.subtype = SpatialMember::class }

        /**
         * A map whose keys are strings and values are primitives, following the JBON2 tag-map
         * specification. The storage persists this as a flat key/value map that supports
         * containment queries.
         *
         * Indexed via [IndexType.TAGS].
         * @since 3.0
         */
        @JvmField
        val TAGS = defIgnoreCase(MemberType::class, "tags") { self -> self.sortOrder = 8; self.subtype = TagsMember::class }

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
        val TAGS_FROM_ARRAY = defIgnoreCase(MemberType::class, "tags_from_array") { self -> self.sortOrder = 9; self.subtype = TagsMember::class }

        /**
         * A list of unique primitive values (booleans, numbers, strings), following the JBON2
         * TagList specification: entries must not be `null` or duplicates, and the order is
         * significant. The storage persists the list unmodified (as a JSON array in `jsonb`), so
         * the element order is guaranteed to be preserved when reading the feature back.
         *
         * This is the default type of the standard `tags` member (the classic XYZ tags array at
         * `properties -> @ns:com:here:xyz -> tags`, e.g. `["foo", "bar"]`). In contrast to
         * [TAGS_FROM_ARRAY] the values are not split into key/value pairs, therefore only full
         * elements can be searched, not keys or values.
         *
         * Indexed via [IndexType.TAG_LIST].
         * @since 3.0
         */
        @JvmField
        val TAG_LIST = defIgnoreCase(MemberType::class, "tag_list") { self -> self.sortOrder = 10; self.subtype = TagListMember::class }
    }

    /**
     * The concrete subtype of this class.
     * @since 3.0
     */
    var subtype: KClass<out Member> = Member::class
        private set

    /**
     * Tests if the given value is of this type.
     * @param value the value to test.
     * @return `true` if the value is of this type; false otherwise.
     */
    fun isInstance(value: Any?): Boolean {
        if (value == null) return false
        return when (this) {
            BOOLEAN -> value is Boolean
            INT8 -> value is Byte
            INT16 -> value is Byte || value is Short
            INT32 -> value is Byte || value is Short || value is Int
            INT64 -> value is Byte || value is Short || value is Int || value is Long || value is Int64
            FLOAT32 -> value is Float
            FLOAT64 -> value is Float || value is Double
            STRING -> value is String
            BYTE_ARRAY -> value is ByteArray
            TUPLE_NUMBER -> value is TupleNumber
            SPATIAL -> value is SpGeometry
            TAGS, TAGS_FROM_ARRAY -> value is TagMap
            TAG_LIST -> value is List<*>
            else -> false
        }
    }

    /**
     * The sort order of the type to ensure no padding is needed, so `INT8`, `FLOAT8`, `INT4`, `FLOAT4`, ...
     * @since 3.0
     */
    var sortOrder: Int = -1
        private set(value) {
            val it: Iterator<MemberType> = iterate(MemberType::class)
            var biggestSortOrder = -1
            while (it.hasNext()) {
                val memberType = it.next()
                if (memberType.sortOrder == value) {
                    throw NakshaException(INITIALIZATION_FAILED, "Found duplicate sortOrder, member $this == $memberType")
                }
                if (memberType.sortOrder > biggestSortOrder) biggestSortOrder = memberType.sortOrder
            }
            var expected = biggestSortOrder+1
            if (value != biggestSortOrder) {
                throw NakshaException(INITIALIZATION_FAILED, "Member $this is expected to have sortOrder $expected, but has $value")
            }
            field = value
        }
}
