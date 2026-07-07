@file:Suppress("SENSELESS_COMPARISON")

package naksha.psql

import naksha.base.JsEnum
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * Constants for the PostgresQL data types, when stringified, returns the PostgresQL name, e.g. "bigint" or "text".
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgType : JsEnum() {
    companion object {
        // https://www.postgresql.org/docs/current/datatype.html
        @JvmField
        @JsStatic
        val NULL = defIgnoreCase(PgType::class, "null") {
            it.byteSize = 0
        }

        @JvmField
        @JsStatic
        val BOOLEAN = defIgnoreCase(PgType::class, "boolean") {
            it.byteSize = 1
        }.alias<PgType>("bool")

        @JvmField
        @JsStatic
        val BOOLEAN_ARRAY = defIgnoreCase(PgType::class, "boolean[]") {
            it.isArray = true
            it.childType = BOOLEAN
        }.alias<PgType>("bool[]")

        @JvmField
        @JsStatic
        val SHORT = defIgnoreCase(PgType::class, "int2") {
            it.byteSize = 2
        }.alias<PgType>("smallint")

        @JvmField
        @JsStatic
        val SHORT_ARRAY = defIgnoreCase(PgType::class, "int2[]") {
            it.isArray = true
            it.childType = SHORT
        }.alias<PgType>("smallint[]")

        @JvmField
        @JsStatic
        val INT = defIgnoreCase(PgType::class, "int4") {
            it.byteSize = 4
        }.alias<PgType>("int").alias<PgType>("integer")

        @JvmField
        @JsStatic
        val INT_ARRAY = defIgnoreCase(PgType::class, "int4[]") {
            it.isArray = true
            it.childType = INT
        }.alias<PgType>("int[]").alias<PgType>("integer[]")

        @JvmField
        @JsStatic
        val INT64 = defIgnoreCase(PgType::class, "int8") {
            it.byteSize = 8
        }.alias<PgType>("bigint")

        @JvmField
        @JsStatic
        val INT64_ARRAY = defIgnoreCase(PgType::class, "int8[]") {
            it.isArray = true
            it.childType = INT64
        }.alias<PgType>("bigint[]")

        @JvmField
        @JsStatic
        val FLOAT = defIgnoreCase(PgType::class, "float4") {
            it.byteSize = 4
        }.alias<PgType>("real")

        @JvmField
        @JsStatic
        val FLOAT_ARRAY = defIgnoreCase(PgType::class, "float4[]") {
            it.isArray = true
            it.childType = FLOAT
        }.alias<PgType>("real[]")

        @JvmField
        @JsStatic
        val DOUBLE = defIgnoreCase(PgType::class, "float8") {
            it.byteSize = 8
        }.alias<PgType>("double precision")

        @JvmField
        @JsStatic
        val DOUBLE_ARRAY = defIgnoreCase(PgType::class, "float8[]") {
            it.isArray = true
            it.childType = DOUBLE
        }.alias<PgType>("double precision[]")

        @JvmField
        @JsStatic
        val STRING = defIgnoreCase(PgType::class, "text")

        @JvmField
        @JsStatic
        val STRING_ARRAY = defIgnoreCase(PgType::class, "text[]") {
            it.isArray = true
            it.childType = STRING
        }

        @JvmField
        @JsStatic
        val BYTE_ARRAY = defIgnoreCase(PgType::class, "bytea")

        @JvmField
        @JsStatic
        val BYTE_ARRAY_ARRAY = defIgnoreCase(PgType::class, "bytea[]") {
            it.isArray = true
            it.childType = BYTE_ARRAY
        }

        /**
         * JSONB column type, bound as text (JSON).
         *
         * Used by storages to materialize [naksha.model.objects.MemberType.TAG_MAP], [naksha.model.objects.MemberType.TAG_MAP_FROM_ARRAY] (JSON object), and [naksha.model.objects.MemberType.TAG_LIST] (JSON array) members.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val JSONB = defIgnoreCase(PgType::class, "jsonb")

        /**
         * `jsonb[]` array type. Element values are JSON text strings (the on-wire form Postgres accepts for `jsonb`).
         *
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val JSONB_ARRAY = defIgnoreCase(PgType::class, "jsonb[]") {
            it.isArray = true
            it.childType = JSONB
        }

        /**
         * Returns the [PgType] from the given string.
         * @param name the name, for example `"int"`.
         * @return the matching [PgType] or `null`, if none matches.
         */
        @JsStatic
        @JvmStatic
        fun of(name: String?): PgType? = getDefined(name, PgType::class)

        /**
         * Detects the [PgType] of the given value, if it has any.
         * @param value the value for which to detect the [PgType]
         * @return the matching [PgType] or `null`, if none matches.
         */
        @JsStatic
        @JvmStatic
        fun ofValue(value: Any?): PgType? {
            // TODO: Implement detection.
            return null
        }

        /**
         * Returns the database column type to be used for a specific [Member].
         * @param member the [Member] to lookup.
         * @return the database column type to be used for a specific [Member].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun ofMember(member: Member): PgType = ofMemberType(member.dataType)

        /**
         * Returns the database column type to be used for a specific [MemberType].
         * @param memberType the [MemberType] to lookup.
         * @return the database column type to be used for a specific [MemberType].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun ofMemberType(memberType: MemberType): PgType = when (memberType) {
            MemberType.BOOLEAN -> BOOLEAN
            MemberType.INT8 -> SHORT
            MemberType.INT16 -> SHORT
            MemberType.INT32 -> INT
            MemberType.INT64 -> INT64
            MemberType.FLOAT32 -> FLOAT
            MemberType.FLOAT64 -> DOUBLE
            MemberType.STRING -> STRING
            // All tag variants are coerced to a jsonb flat map (GIN-indexed), so all three map to jsonb.
            MemberType.TAG_MAP -> JSONB
            MemberType.TAG_MAP_FROM_ARRAY -> JSONB
            MemberType.TAG_LIST -> JSONB
            // MemberType.BYTE_ARRAY -> BYTE_ARRAY
            // MemberType.TUPLE_NUMBER -> BYTE_ARRAY
            // MemberType.SPATIAL -> BYTE_ARRAY (TWKB)
            else -> BYTE_ARRAY
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgType::class

    override fun initClass() {}

    /**
     * The size of the type, when being stored and not _null_, or `-1`, if the type has a dynamic
     * @since 3.0
     */
    var byteSize: Int = -1
        get() = if (field == null) -1 else field
        private set

    /**
     * If this type is an array.
     * @since 3.0
     */
    var isArray: Boolean = false
        get() = if (field == null) false else field
        private set

    /**
     * If this is an array, the child type.
     * @since 3.0
     */
    var childType: PgType? = null
        private set

    /**
     * Converts the given value in this type, so that it can be given to JDBC.
     *
     * For example, converts a `List<String>` into a `String[]`.
     * @param value the value to convert into this type.
     * @return the value so that it can be used with JDBC.
     */
    fun convertValue(value: Any?): Any? {
        // TODO: We need to convert certain values to postgres valid ones
        //       For lists, we need to convert them into typed arrays.
        return value
    }
}