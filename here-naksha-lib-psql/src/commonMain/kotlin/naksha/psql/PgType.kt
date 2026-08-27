@file:Suppress("SENSELESS_COMPARISON")

package naksha.psql

import naksha.base.AnyList
import naksha.base.Int64
import naksha.base.JsEnum
import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.illegalArg
import naksha.base.internalError
import naksha.base.proxy
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

        /**
         * The PostgresSQ data type for `null`, being `null`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val NULL = defIgnoreCase(PgType::class, "null") {
            it.byteSize = 0
        }

        /**
         * The PostgresSQ data type for [Boolean], being `boolean`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val BOOLEAN = defIgnoreCase(PgType::class, "boolean") {
            it.byteSize = 1
            it.klass = Boolean::class
        }.alias<PgType>("bool")

        @JvmField
        @JsStatic
        val BOOLEAN_ARRAY = defIgnoreCase(PgType::class, "boolean[]") {
            it.componentType = BOOLEAN
            it.klass = BooleanArray::class
        }.alias<PgType>("bool[]")

        /**
         * The PostgresSQ data type for [Short], being `int2`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SHORT = defIgnoreCase(PgType::class, "int2") {
            it.byteSize = 2
            it.klass = Short::class
        }.alias<PgType>("smallint")

        @JvmField
        @JsStatic
        val SHORT_ARRAY = defIgnoreCase(PgType::class, "int2[]") {
            it.componentType = SHORT
            it.klass = ShortArray::class
        }.alias<PgType>("smallint[]")

        /**
         * The PostgresSQ data type for [Int], being `int4`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val INT = defIgnoreCase(PgType::class, "int4") {
            it.byteSize = 4
            it.klass = Int::class
        }.alias<PgType>("int").alias<PgType>("integer")

        @JvmField
        @JsStatic
        val INT_ARRAY = defIgnoreCase(PgType::class, "int4[]") {
            it.componentType = INT
            it.klass = IntArray::class
        }.alias<PgType>("int[]").alias<PgType>("integer[]")

        /**
         * The PostgresSQ data type for [Long], being `int8`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val INT64 = defIgnoreCase(PgType::class, "int8") {
            it.byteSize = 8
            it.klass = Long::class
        }.alias<PgType>("bigint")

        @JvmField
        @JsStatic
        val INT64_ARRAY = defIgnoreCase(PgType::class, "int8[]") {
            it.componentType = INT64
            it.klass = LongArray::class
        }.alias<PgType>("bigint[]")

        /**
         * The PostgresSQ data type for [Float], being `float4`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val FLOAT = defIgnoreCase(PgType::class, "float4") {
            it.byteSize = 4
            it.klass = Float::class
        }.alias<PgType>("real")

        @JvmField
        @JsStatic
        val FLOAT_ARRAY = defIgnoreCase(PgType::class, "float4[]") {
            it.componentType = FLOAT
            it.klass = FloatArray::class
        }.alias<PgType>("real[]")

        /**
         * The PostgresSQ data type for [Double], being `float8`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val DOUBLE = defIgnoreCase(PgType::class, "float8") {
            it.byteSize = 8
            it.klass = Double::class
        }.alias<PgType>("double precision")

        @JvmField
        @JsStatic
        val DOUBLE_ARRAY = defIgnoreCase(PgType::class, "float8[]") {
            it.componentType = DOUBLE
            it.klass = DoubleArray::class
        }.alias<PgType>("double precision[]")

        /**
         * The PostgresSQ data type for [String], being `text`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val STRING = defIgnoreCase(PgType::class, "text") {
            it.klass = String::class
        }

        @JvmField
        @JsStatic
        val STRING_ARRAY = defIgnoreCase(PgType::class, "text[]") {
            it.componentType = STRING
            it.klass = Array::class
        }

        /**
         * The PostgresSQ data type for [ByteArray], being `bytea`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val BYTE_ARRAY = defIgnoreCase(PgType::class, "bytea") {
            it.klass = ByteArray::class
        }

        @JvmField
        @JsStatic
        val BYTE_ARRAY_ARRAY = defIgnoreCase(PgType::class, "bytea[]") {
            it.componentType = BYTE_ARRAY
            it.klass = Array::class
        }

        /**
         * JSONB column type, bound as text (JSON).
         *
         * Used by storages to materialize [naksha.model.objects.MemberType.TAG_MAP], [naksha.model.objects.MemberType.TAG_MAP_FROM_ARRAY] (JSON object), and [naksha.model.objects.MemberType.TAG_LIST] (JSON array) members.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val JSONB = defIgnoreCase(PgType::class, "jsonb") {
            it.klass = String::class
        }

        /**
         * `jsonb[]` array type. Element values are JSON text strings (the on-wire form Postgres accepts for `jsonb`).
         *
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val JSONB_ARRAY = defIgnoreCase(PgType::class, "jsonb[]") {
            it.componentType = JSONB
            it.klass = Array::class
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
         *
         * If the given `value` is `null`, then [NULL] is returned, the value `null` means that the detection failed.
         *
         * ### WARNING
         * This method is unable to detect [JSONB] type!
         * @param value the value for which to detect the [PgType]
         * @return the matching [PgType] or `null` if none matches.
         */
        @JsStatic
        @JvmStatic
        fun ofValue(value: Any?): PgType? {
            when (value) {
                null -> return NULL
                is Boolean -> return BOOLEAN
                is Byte -> return SHORT
                is Short -> return SHORT
                is Int -> return INT
                is Long -> return INT64
                is Float -> return FLOAT
                is Double -> return DOUBLE
                is String -> return STRING
                is ByteArray -> return BYTE_ARRAY
                is List<*>,
                is ListProxy<*> -> {
                    val elementType = value.firstOrNull()?.let { ofValue(it) } ?: return null
                    return when (elementType) {
                        BOOLEAN -> BOOLEAN_ARRAY
                        SHORT -> SHORT_ARRAY
                        INT -> INT_ARRAY
                        INT64 -> INT64_ARRAY
                        FLOAT -> FLOAT_ARRAY
                        DOUBLE -> DOUBLE_ARRAY
                        STRING -> STRING_ARRAY
                        BYTE_ARRAY -> BYTE_ARRAY_ARRAY
                        else -> null
                    }
                }
            }
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
            MemberType.TAG_MAP -> JSONB
            MemberType.TAG_MAP_FROM_ARRAY -> JSONB
            MemberType.TAG_LIST -> STRING_ARRAY
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
     * The [KClass] of the type.
     *
     * ### Warning
     * Kotlin sadly does not support real typed arrays, so for example for `Array<String>` is not supported, you will get `Array::class`!
     * @since 3.0
     */
    var klass: KClass<*>? = null
        private set

    /**
     * The size of the type, when being stored and not _null_, or `-1`, if the type has a dynamic
     * @since 3.0
     */
    var byteSize: Int = -1
        get() = if (field == null) -1 else field // Note: JavaScript hack!
        private set

    /**
     * If this type is an array.
     * @since 3.0
     */
    val isArray: Boolean
        get() = componentType != null

    /**
     * If this is an array, the type of the elements _(aka component type)_.
     * @since 3.0
     */
    var componentType: PgType? = null
        private set

    /**
     * Converts the given value in this type, so that it can be given to JDBC.
     *
     * For example, converts a `List<String>` into a `String[]`.
     * @param value the value to convert into this type.
     * @return the value so that it can be used with JDBC.
     * @throws naksha.base.NakshaException if the value can't be converted.
     */
    fun convertValue(value: Any?): Any? {
        return when (this) {
            NULL -> null
            BOOLEAN -> value as? Boolean?
                    ?: throw illegalArg("The given value is not a boolean: $value")
            SHORT, INT -> (value as? Number?)?.toInt()
                    ?: throw illegalArg("The given value is no number: $value")
            INT64 -> if (value is Number) value.toLong() else if (value is Int64) value.toLong()
                    else throw illegalArg("The given value is no number: $value")
            FLOAT -> (value as? Number?)?.toFloat()
                    ?: throw illegalArg("The given value is no number: $value")
            DOUBLE -> (value as? Number?)?.toDouble()
                    ?: throw illegalArg("The given value is no number: $value")
            STRING -> value as? String?
                ?: throw illegalArg("The given value is not a string: $value")
            BYTE_ARRAY -> value as? ByteArray?
                ?: throw illegalArg("The given value is not a string: $value")
            JSONB -> toJSON(value)

            BOOLEAN_ARRAY -> value as? BooleanArray ?: (value.proxy(AnyList::class)?.toBooleanArray(true)
                    ?: throw illegalArg("The given value is not an list: $value"))
            SHORT_ARRAY -> value as? ShortArray ?: value.proxy(AnyList::class)?.toShortArray(true)
                ?: throw illegalArg("The given value is not an list: $value")
            INT_ARRAY -> value as? IntArray ?: value.proxy(AnyList::class)?.toIntArray(true)
                ?: throw illegalArg("The given value is not an list: $value")
            INT64_ARRAY -> value as? LongArray ?: value.proxy(AnyList::class)?.toLongArray(true)
                ?: throw illegalArg("The given value is not an list: $value")
            // We do not support Array<ByteArray>, it's hard to detect cross-platform!
            BYTE_ARRAY_ARRAY -> value.proxy(AnyList::class)?.toByteArrayArray(true)
                    ?: throw illegalArg("The given value is not an list: $value")
            // We do not support Array<String>, it's hard to detect cross-platform!
            JSONB_ARRAY -> value.proxy(AnyList::class)?.toJsonArray()
                ?: throw illegalArg("The given value is not an list: $value")
            else -> throw internalError("Missing when-case for this Postgres Type: $this")
        }
    }
}