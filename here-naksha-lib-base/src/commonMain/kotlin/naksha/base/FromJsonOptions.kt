package naksha.base

import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Options for the JSON parsing.
 * @property parseDataUrl Set to _true_, the JSON parser should parse [Data-URLs](https://datatracker.ietf.org/doc/html/rfc2397)
 * found in values of the JSON. Actually, this enables support for 64-bit integer deserialization in browsers.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class FromJsonOptions(val parseDataUrl: Boolean = false) {
    @OptIn(ExperimentalJsStatic::class)
    companion object FromJsonOptionsCompanion {
        /**
         * The default JSON parsing options being used, when none are given explicitly.
         */
        @JvmStatic
        @JsStatic
        val DEFAULT = FromJsonOptions()
    }
}