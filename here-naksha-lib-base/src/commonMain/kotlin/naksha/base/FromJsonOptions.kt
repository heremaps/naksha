package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Options for the JSON parsing.
 * @property parseDataUrl Set to _true_, the JSON parser should convert [Data-URLs](https://datatracker.ietf.org/doc/html/rfc2397)
 * into [PlatformDataView], [Int64], or other proprietary objects. Actually, this enables as well support for 64-bit integer deserialization in browsers _(`data:bigint,1099511627776`)_.
 * @property detectors The type detectors to use, if `null`, then [Platform.globalDetectors] are used.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class FromJsonOptions @JvmOverloads constructor(
    val parseDataUrl: Boolean = false,
    val detectors: AtomicSet<TypeDetector>? = null
) {
    @OptIn(ExperimentalJsStatic::class)
    companion object FromJsonOptionsCompanion {
        /**
         * The [PlatformType] of [FromJsonOptions].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(FromJsonOptions::class).withPackageName(PACKAGE_NAME)

        /**
         * The default JSON parsing options being used, when none are given explicitly.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val DEFAULT = FromJsonOptions()
    }
}