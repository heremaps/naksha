package naksha.model

import naksha.base.NormalizerForm
import naksha.base.NormalizerForm.NFD
import naksha.base.NormalizerForm.NFKC
import naksha.base.Platform
import naksha.model.TagNormalizer.TagNormalizer_C.normalizeTag
import naksha.model.TagNormalizer.TagNormalizer_C.splitNormalizedTag
import kotlin.js.JsExport
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
            "ref_" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = false),
            "sourceID_" to TagProcessingPolicy(NFKC, removeNonAscii = false, lowercase = false, split = false),
            "~" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true),
            "#" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true)
        )

        private val AS_IS: CharArray = CharArray(128 - 32) { (it + 32).toChar() }
        private val TO_LOWER: CharArray = CharArray(128 - 32) { (it + 32).toChar().lowercaseChar() }

        /**
         * Main method for raw tag normalization. See[TagNormalizer] doc for more
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
         * Main method for normalized tag splitting. See[TagNormalizer] doc for more
         */
        internal fun splitNormalizedTag(normalizedTag: String): Pair<String, Any?> {
            if (!policyFor(normalizedTag).split) {
                return normalizedTag to null
            }
            val i = normalizedTag.indexOf('=')
            val key: String
            val value: Any?
            if (i >= 1) {
                if (normalizedTag[i - 1] == ':') { // :=
                    key = normalizedTag.substring(0, i - 1).trim()
                    val raw = normalizedTag.substring(i + 1).trim()
                    value = if ("true".equals(raw, ignoreCase = true)) {
                        true
                    } else if ("false".equals(raw, ignoreCase = true)) {
                        false
                    } else {
                        raw.toDouble()
                    }
                } else {
                    key = normalizedTag.substring(0, i).trim()
                    value = normalizedTag.substring(i + 1).trim()
                }
            } else {
                key = normalizedTag
                value = null
            }
            return key to value
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
