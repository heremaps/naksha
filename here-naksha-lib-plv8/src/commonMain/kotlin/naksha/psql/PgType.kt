package naksha.psql

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * Constants for the PostgresQL data types.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgType : JsEnum() {
    companion object {
        // https://www.postgresql.org/docs/current/datatype.html
        @JvmField
        @JsStatic
        val NULL = defIgnoreCase(PgType::class, "null")

        @JvmField
        @JsStatic
        val BOOLEAN = defIgnoreCase(PgType::class, "boolean").alias(PgType::class, "bool")

        @JvmField
        @JsStatic
        val BOOLEAN_ARRAY = defIgnoreCase(PgType::class, "boolean[]").alias(PgType::class, "bool[]")

        @JvmField
        @JsStatic
        val SHORT = defIgnoreCase(PgType::class, "smallint").alias(PgType::class, "int2")

        @JvmField
        @JsStatic
        val SHORT_ARRAY = defIgnoreCase(PgType::class, "smallint[]").alias(PgType::class, "int2[]")

        @JvmField
        @JsStatic
        val INT = defIgnoreCase(PgType::class, "integer").alias(PgType::class, "int4").alias(PgType::class, "int")

        @JvmField
        @JsStatic
        val INT_ARRAY = defIgnoreCase(PgType::class, "integer[]")
            .alias(PgType::class, "int4[]").alias(PgType::class, "int[]")

        @JvmField
        @JsStatic
        val INT64 = defIgnoreCase(PgType::class, "bigint").alias(PgType::class, "int8")

        @JvmField
        @JsStatic
        val INT64_ARRAY = defIgnoreCase(PgType::class, "bigint[]").alias(PgType::class, "int8[]")

        @JvmField
        @JsStatic
        val FLOAT = defIgnoreCase(PgType::class, "real").alias(PgType::class, "float4")

        @JvmField
        @JsStatic
        val FLOAT_ARRAY = defIgnoreCase(PgType::class, "real[]").alias(PgType::class, "float4[]")

        @JvmField
        @JsStatic
        val DOUBLE = defIgnoreCase(PgType::class, "double precision").alias(PgType::class, "float8")

        @JvmField
        @JsStatic
        val DOUBLE_ARRAY = defIgnoreCase(PgType::class, "double precision[]").alias(PgType::class, "float8[]")

        @JvmField
        @JsStatic
        val STRING = defIgnoreCase(PgType::class, "text")

        @JvmField
        @JsStatic
        val STRING_ARRAY = defIgnoreCase(PgType::class, "text[]")

        @JvmField
        @JsStatic
        val BYTE_ARRAY = defIgnoreCase(PgType::class, "bytea")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgType::class

    override fun initClass() {
        register(PgType::class, PgType::class)
    }
}