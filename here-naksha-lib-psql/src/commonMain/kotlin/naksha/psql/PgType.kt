@file:Suppress("SENSELESS_COMPARISON")

package naksha.psql

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Constants for the PostgresQL data types, when stringified, returns the PostgresQL name, e.g. "bigint" or "text".
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgType : PlatformEnum() {
    companion object PgType_C {
        /**
         * The [PlatformType] of [PgType].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgType::class).withPackageName(PACKAGE_NAME)

        // https://www.postgresql.org/docs/current/datatype.html
        @JvmField
        @JsStatic
        val NULL = defIgnoreCase(TYPE, "null") {
            it.byteSize = 0
        }

        @JvmField
        @JsStatic
        val BOOLEAN = defIgnoreCase(TYPE, "boolean") {
            it.byteSize = 1
        }.alias<PgType>("bool")

        @JvmField
        @JsStatic
        val BOOLEAN_ARRAY = defIgnoreCase(TYPE, "boolean[]") {
            it.isArray = true
            it.childType = BOOLEAN
        }.alias<PgType>("bool[]")

        @JvmField
        @JsStatic
        val SHORT = defIgnoreCase(TYPE, "int2") {
            it.byteSize = 2
        }.alias<PgType>("smallint")

        @JvmField
        @JsStatic
        val SHORT_ARRAY = defIgnoreCase(TYPE, "int2[]") {
            it.isArray = true
            it.childType = SHORT
        }.alias<PgType>("smallint[]")

        @JvmField
        @JsStatic
        val INT = defIgnoreCase(TYPE, "int4") {
            it.byteSize = 4
        }.alias<PgType>("int").alias<PgType>("integer")

        @JvmField
        @JsStatic
        val INT_ARRAY = defIgnoreCase(TYPE, "int4[]") {
            it.isArray = true
            it.childType = INT
        }.alias<PgType>("int[]").alias<PgType>("integer[]")

        @JvmField
        @JsStatic
        val INT64 = defIgnoreCase(TYPE, "int8") {
            it.byteSize = 8
        }.alias<PgType>("bigint")

        @JvmField
        @JsStatic
        val INT64_ARRAY = defIgnoreCase(TYPE, "int8[]") {
            it.isArray = true
            it.childType = INT64
        }.alias<PgType>("bigint[]")

        @JvmField
        @JsStatic
        val FLOAT = defIgnoreCase(TYPE, "float4") {
            it.byteSize = 4
        }.alias<PgType>("real")

        @JvmField
        @JsStatic
        val FLOAT_ARRAY = defIgnoreCase(TYPE, "float4[]") {
            it.isArray = true
            it.childType = FLOAT
        }.alias<PgType>("real[]")

        @JvmField
        @JsStatic
        val DOUBLE = defIgnoreCase(TYPE, "float8") {
            it.byteSize = 8
        }.alias<PgType>("double precision")

        @JvmField
        @JsStatic
        val DOUBLE_ARRAY = defIgnoreCase(TYPE, "float8[]") {
            it.isArray = true
            it.childType = DOUBLE
        }.alias<PgType>("double precision[]")

        @JvmField
        @JsStatic
        val STRING = defIgnoreCase(TYPE, "text")

        @JvmField
        @JsStatic
        val STRING_ARRAY = defIgnoreCase(TYPE, "text[]") {
            it.isArray = true
            it.childType = STRING
        }

        @JvmField
        @JsStatic
        val BYTE_ARRAY = defIgnoreCase(TYPE, "bytea")

        @JvmField
        @JsStatic
        val BYTE_ARRAY_ARRAY = defIgnoreCase(TYPE, "bytea[]") {
            it.isArray = true
            it.childType = BYTE_ARRAY
        }

        /**
         * Returns the [PgType] from the given string.
         * @param name the name, for example `"int"`.
         * @return the matching [PgType] or `null`, if none matches.
         */
        @JsStatic
        @JvmStatic
        fun of(name: String?): PgType? = getDefined(name, TYPE)
    }

    override fun namespace() = TYPE
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
}