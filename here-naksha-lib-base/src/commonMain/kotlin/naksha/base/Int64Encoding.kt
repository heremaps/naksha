package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The way how the JSON serializer should encode 64-bit integers.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Int64Encoding : JsEnum() {
    companion object Int64EncodingCompanion {
        /**
         * The [PlatformType] of [DoubleList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<Int64Encoding> = forKClass(Int64Encoding::class).withPackageName(PACKAGE_NAME)

        /**
         * Encode as 64-bit integer (_default_).
         */
        @JvmField
        @JsStatic
        val AS_INTEGER = def(TYPE, "int")

        /**
         * Encode as double, accept the risk of losing precision.
         */
        @JvmField
        @JsStatic
        val AS_DOUBLE = def(TYPE, "double")

        /**
         * Encode as decimal string.
         */
        @JvmField
        @JsStatic
        val AS_STRING = def(TYPE, "string")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;dec,123456789`.
         */
        @JvmField
        @JsStatic
        val AS_DECIMAL_DATA_URL = def(TYPE, "decimal_data_url")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;hex,1f3e4495`.
         */
        @JvmField
        @JsStatic
        val AS_HEX_DATA_URL = def(TYPE, "hex_data_url")

        /**
         * Encode as decimal [Data-URL](https://datatracker.ietf.org/doc/html/rfc2397), e.g. `data:int64;base64,fewE2ed23=`.
         */
        @JvmField
        @JsStatic
        val AS_BASE64_DATA_URL = def(TYPE, "base64_data_url")
    }

    override fun namespace(): PlatformType<out JsEnum> = TYPE

    override fun initClass() {}
}