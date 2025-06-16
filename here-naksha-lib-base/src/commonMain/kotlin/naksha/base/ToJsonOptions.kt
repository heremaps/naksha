package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Options for the JSON parsing.
 * @property int64Encoding The encoding to select to serialize 64-bit integers.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class ToJsonOptions(val int64Encoding: Int64Encoding = Int64Encoding.AS_INTEGER) {
    companion object ToJsonOptions_C {
        /**
         * The [PlatformType] of [ToJsonOptions].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ToJsonOptions::class).withPackageName(PACKAGE_NAME)

        /**
         * The default JSON serialization options being used, when none are given explicitly.
         */
        @JvmField
        @JsStatic
        val DEFAULT = ToJsonOptions()

        init { initialize() }
    }
}