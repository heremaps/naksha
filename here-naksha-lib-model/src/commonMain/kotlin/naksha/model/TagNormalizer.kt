package naksha.model

import naksha.base.NakshaException
import naksha.base.NormalizerForm
import naksha.base.NormalizerForm.NFD
import naksha.base.NormalizerForm.NFKC
import naksha.base.Platform
import naksha.base.illegalArg
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * An object used for Tag normalization and _(optional)_ splitting.
 *
 * Process of normalization happens in [normalizeTag] method and includes following steps:
 *
 * 1) Always: apply [UNICODE normalization form](https://www.unicode.org/reports/tr15/) (see [NormalizerForm])
 * 2) Conditional: lowercase the tag
 * 3) Conditional: remove all non-ASCII characters
 * 4) Optional: split
 *
 * Process of splitting tags is done when [TagList.toTagMap] is invoked. Note that not all tags can be split, it depends on their prefix.
 *
 * Summarised per-prefix behavior of the tag-normalization:
 *
 * - `sourceID_.*` —> NFKC
 *     - **lowercase**: `false`, **ASCII**: `false`, **split**: `false`
 * - `ref_.*` —> NFKC, reference
 *     - **lowercase**: `false`, **ASCII**: `false`, **split**: `false`
 * - `@.*` —> NFKC, splittable reference
 *     - **lowercase**: `false`, **ASCII**: `false`, **split**: `true`
 * - `#.*` —> NFD,
 *     - **lowercase**: `false`, **ASCII**: `true`, **split**: `true`
 * - `~.*` —> NFD _(Web-URL friend variant of `#`)_
 *     - **lowercase**: `false`, **ASCII**: `true`, **split**: `true`
 * - otherwise: NFD
 *     - **lowercase**: `true`, **ASCII**: `true`, **split**: `true`
 *
 * Note, the `sourceID_.*` is a historic artifact, it was the first exception being made from normalization. In the aftermath of this, rules were introduced, specifically the `ref_.*` and `@.*` rule, to avoid more exceptions.
 *
 * By default, (if no special prefix is found) tag is normalized with NFD, lowercased, cleaned of non-ASCII and is splittable.
 *
 * If you you copy identifiers into tags, it is strongly recommended to use the `@` _(at)_ prefix. For example `@sourceId=Ref$1`. This keeps the tag unchanged and allows to query for that exact tag, plus it allows to split the tags in code to tests if there is a `sourceId`:
 * ```kotlin
 * val tagList = TagList("@sourceId=Ref$1")
 * val tagMap = tagList.toTagMap()
 * println( tagMap.containsKey("@sourceId") )
 * println( tagMap.get("@sourceId") )
 * ```
 * @since 3.0
 */
@JsExport
class TagNormalizer private constructor() {

    companion object TagNormalizer_C {
        private val DEFAULT_POLICY = TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = true, split = true)
        private val PREFIX_TO_POLICY = mapOf(
            "@" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = true),
            "~" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true),
            // Downward compatibility workarounds:
            "ref_" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = false),
            "sourceID_" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = false),
            "xyz_source_id_" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = false),
            "#" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true)
        )

        private val AS_IS: CharArray = CharArray(128 - 32) { (it + 32).toChar() }
        private val TO_LOWER: CharArray = CharArray(128 - 32) { (it + 32).toChar().lowercaseChar() }

        /**
         * Normalize the given tag using the [tag-normalization rules][TagNormalizer].
         * @param tag the tag.
         * @return the normalized tag, see [TagNormalizer].
         * @see TagNormalizer
         * @since 3.0
         */
        @JvmStatic
        fun normalizeTag(tag: String): String {
            val policy = policyFor(tag)
            val normalized = Platform.normalize(tag, policy.normalizerForm)
            return if (policy.lowercase) {
                if (policy.removeNonAscii) {
                    removeNonAscii(normalized, TO_LOWER)
                } else {
                    normalized.lowercase()
                }
            } else if (policy.removeNonAscii){
                removeNonAscii(normalized, AS_IS)
            } else {
                normalized
            }
        }

        private fun removeNonAscii(input: String, outputCharacterSet: CharArray): String {
            val sb = StringBuilder()
            for (element in input) {
                val c = (element.code - 32).toChar()
                if (c.code < outputCharacterSet.size) {
                    sb.append(outputCharacterSet[c.code])
                }
            }
            return sb.toString()
        }


        /**
         * Split a tag at the last equal sign (`=`).
         *
         * If the equal sign is preceded by a colon (`:`) the value is parsed into `Boolean`, or `Double` with a fallback to [String].
         * @param tag the tag to split.
         * @return the _key_ and _value_, with _value_ potentially being `null`.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun splitTag(tag: String): Pair<String, Any?> {
            if (!policyFor(tag).split) {
                return tag to null
            }
            val pos = tag.lastIndexOf('=')
            if (pos < 1) return tag to null
            if (tag[pos - 1] == ':') { // :=
                val key = tag.substring(0, pos - 1).trim()
                val raw = tag.substring(pos + 1).trim()
                if ("true".equals(raw, ignoreCase = true)) return key to true
                if ("false".equals(raw, ignoreCase = true)) return key to false
                return try {
                    key to raw.toDouble()
                } catch (_: NumberFormatException) {
                    // This is an illegal tag encoding, we do not split
                    tag to raw
                }
            }
            // Assignment failed, use equals.
            val key = tag.substring(0, pos).trim()
            val value = tag.substring(pos + 1).trim()
            return key to value
        }

        /**
         * Converts _(key, value)_ pair to String, so it can be part of [TagList].
         *
         * The result depends on the value:
         * - `null` — value is omitted
         *     - (`"foo"`, `null`) —> `"foo"`
         * - [String] — value is separated with equal sign (`=`)
         *     - (`"foo"`, `"bar"`) -> `"foo=bar"`
         * - [Boolean] or [Number] – value is separated with colon-equal (`:=`)
         *     - (`"foo"`, `12.34`) —> `"foo:=12.34"`
         *     - (`"foo"`, `true`) —> `"foo:=true"`
         *
         * @param key the key to join.
         * @param value the value to join.
         * @return the stringified tag.
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given value is not `null`, [String], [Boolean] or [Number].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun joinTag(key: String, value: Any?): String =
            when (value) {
                null -> key
                is String -> "$key=$value"
                is Boolean, is Long -> "$key:=$value"
                is Number -> "$key:=${value.toDouble()}"
                else -> throw illegalArg("Tag values can only be String, Boolean or Number, found: $key = $value")
            }

        private fun policyFor(tag: String): TagProcessingPolicy {
            for ((prefix, policy) in PREFIX_TO_POLICY) {
                if (tag.startsWith(prefix)) return policy
            }
            return DEFAULT_POLICY
        }
    }
}

private data class TagProcessingPolicy(
    val normalizerForm: NormalizerForm,
    val removeNonAscii: Boolean,
    val lowercase: Boolean,
    val split: Boolean
)
