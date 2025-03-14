@file:OptIn(ExperimentalJsExport::class)

package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Helper for tags encoding in [Flags].
 */
@JsExport
class TagsEncoding private constructor(): FlagsBits() {
    companion object TagsEncoding_C {
        /**
         * A JSON encoded using [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md) encoding.
         */
        const val JBON = 0 shl TAGS_SHIFT

        /**
         * A JSON encoded using [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md) encoding, compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val JBON_GZIP = 1 shl TAGS_SHIFT

        /**
         * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value.
         */
        const val JSON = 2 shl TAGS_SHIFT

        /**
         * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value, compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val JSON_GZIP = 3 shl TAGS_SHIFT
    }
}