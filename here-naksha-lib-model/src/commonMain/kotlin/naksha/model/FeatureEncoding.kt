@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_SHIFT
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Helper for feature encoding in [Flags].
 */
@JsExport
class FeatureEncoding private constructor() {
    companion object FeatureEncodingCompanion {
        /**
         * The [PlatformType] of [FeatureEncoding].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<FeatureEncoding> = forKClass(FeatureEncoding::class).withPackageName(naksha.jbon.PACKAGE_NAME)

        /**
         * A JSON encoded using [JBON](https://github.com/xeus2001/xyz-hub/blob/v3/docs/JBON.md) encoding.
         */
        const val JBON = 0 shl FEATURE_SHIFT

        /**
         * A JSON encoded using [JBON](https://github.com/xeus2001/xyz-hub/blob/v3/docs/JBON.md) encoding, compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val JBON_GZIP = 1 shl FEATURE_SHIFT

        /**
         * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value.
         */
        const val JSON = 2 shl FEATURE_SHIFT

        /**
         * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value, compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val JSON_GZIP = 3 shl FEATURE_SHIFT
    }

}