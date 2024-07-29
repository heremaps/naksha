package naksha.model

import kotlin.js.JsExport

/**
 * Helper for tags encoding in [Flags].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
object TagsEncoding : FlagsBits() {
    /**
     * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value.
     */
    const val JSON = 0 shl TAGS_SHIFT

    /**
     * A standard [JSON](https://datatracker.ietf.org/doc/html/rfc8259) encoded value, compressed using
     * [GZIP](https://en.wikipedia.org/wiki/Gzip).
     */
    const val JSON_GZIP = 1 shl TAGS_SHIFT

    /**
     * A JSON encoded using [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md) encoding.
     */
    const val JBON = 2 shl TAGS_SHIFT

    /**
     * A JSON encoded using [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md) encoding, compressed using
     * [GZIP](https://en.wikipedia.org/wiki/Gzip).
     */
    const val JBON_GZIP = 3 shl TAGS_SHIFT
}