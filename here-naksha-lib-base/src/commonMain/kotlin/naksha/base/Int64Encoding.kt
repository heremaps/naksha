package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The way how the JSON serializer should encode 64-bit integers.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Int64Encoding : JsEnum() {
    companion object Int64EncodingCompanion {
        /**
         * Encode as 64-bit integer (_default_).
         */
        @JvmField
        @JsStatic
        val AS_INTEGER = def(Int64Encoding::class, "int")

        /**
         * Encode as double, accept the risk of losing precision.
         */
        @JvmField
        @JsStatic
        val AS_DOUBLE = def(Int64Encoding::class, "double")

        /**
         * Encode as decimal string.
         */
        @JvmField
        @JsStatic
        val AS_STRING = def(Int64Encoding::class, "string")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;dec,123456789`.
         */
        @JvmField
        @JsStatic
        val AS_DECIMAL_DATA_URL = def(Int64Encoding::class, "decimal_data_url")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;hex,1f3e4495`.
         */
        @JvmField
        @JsStatic
        val AS_HEX_DATA_URL = def(Int64Encoding::class, "hex_data_url")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;base64,fewE2ed23=`.
         */
        @JvmField
        @JsStatic
        val AS_BASE64_DATA_URL = def(Int64Encoding::class, "base64_data_url")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = Int64Encoding::class
    override fun initClass() {}
}