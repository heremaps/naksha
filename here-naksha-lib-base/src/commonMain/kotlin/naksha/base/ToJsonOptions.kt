package naksha.base

import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Options for the JSON parsing.
 * @property int64Encoding The encoding to select to serialize 64-bit integers.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class ToJsonOptions(val int64Encoding: Int64Encoding = Int64Encoding.AS_INTEGER) {
    @OptIn(ExperimentalJsStatic::class)
    companion object {
        /**
         * The default JSON serialization options being used, when none are given explicitly.
         */
        @JvmStatic
        @JsStatic
        val DEFAULT = ToJsonOptions()
    }
}