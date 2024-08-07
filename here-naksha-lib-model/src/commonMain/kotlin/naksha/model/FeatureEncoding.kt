@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Helper for feature encoding in [Flags].
 */
@JsExport
class FeatureEncoding : FlagsBits() {
    companion object FeatureEncoding_C {
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