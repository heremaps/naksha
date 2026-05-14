@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The logical data type of a [CustomMember].
 *
 * - Primitives: [BOOLEAN], [INT8], [INT16], [INT32], [INT64], [FLOAT32], [FLOAT64], [STRING], [BYTE_ARRAY].
 * - [FLAT_MAP]: a virtual type for a map whose values are all primitives and whose keys are all strings.
 * - [TAGS]: a virtual type whose input is a string-array using the Naksha tag syntax (`key=value`, `key:=number`, ...), and whose materialized form is a [FLAT_MAP].
 *
 * Storages decide how to materialize these (e.g. `lib-psql` translates [FLAT_MAP] and [TAGS] to `jsonb`).
 * @since 3.0
 */
@JsExport
class CustomMemberType : JsEnum() {

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = CustomMemberType::class

    override fun initClass() {}

    companion object CustomMemberType_C {
        /**
         * Boolean.
         * @since 3.0
         */
        @JvmField
        val BOOLEAN = defIgnoreCase(CustomMemberType::class, "boolean")

        /**
         * 8-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT8 = defIgnoreCase(CustomMemberType::class, "int8")

        /**
         * 16-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT16 = defIgnoreCase(CustomMemberType::class, "int16")

        /**
         * 32-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT32 = defIgnoreCase(CustomMemberType::class, "int32")

        /**
         * 64-bit signed integer.
         * @since 3.0
         */
        @JvmField
        val INT64 = defIgnoreCase(CustomMemberType::class, "int64")

        /**
         * 32-bit IEEE-754 floating point.
         * @since 3.0
         */
        @JvmField
        val FLOAT32 = defIgnoreCase(CustomMemberType::class, "float32")

        /**
         * 64-bit IEEE-754 floating point.
         * @since 3.0
         */
        @JvmField
        val FLOAT64 = defIgnoreCase(CustomMemberType::class, "float64")

        /**
         * Variable-length UTF-8 text.
         * @since 3.0
         */
        @JvmField
        val STRING = defIgnoreCase(CustomMemberType::class, "string")

        /**
         * Raw byte array.
         * @since 3.0
         */
        @JvmField
        val BYTE_ARRAY = defIgnoreCase(CustomMemberType::class, "byte_array")

        /**
         * A map whose values are all primitives and whose keys are all strings.
         *
         * Storages typically materialize this as a single document column (e.g. `jsonb` in PostgreSQL).
         * @since 3.0
         */
        @JvmField
        val FLAT_MAP = defIgnoreCase(CustomMemberType::class, "flat_map")

        /**
         * A string-array using Naksha tag syntax that is expanded into a [FLAT_MAP] at write time.
         *
         * Input is `["key=value", "name:=42"]`; materialized form is `{"key":"value", "name":42}`.
         * @since 3.0
         */
        @JvmField
        val TAGS = defIgnoreCase(CustomMemberType::class, "tags")
    }
}
