package naksha.model

import naksha.base.NormalizerForm
import naksha.base.NormalizerForm.NFD
import naksha.base.NormalizerForm.NFKC
import naksha.base.Platform
import naksha.model.TagNormalizer.TagNormalizer_C.normalizeTag
import naksha.model.TagNormalizer.TagNormalizer_C.splitNormalizedTag

/**
 * An object used for Tag normalization and splitting.
 *
 * Process of normalization happens in [normalizeTag] method and includes following steps:
 * 1) Always: apply normalization form (see [NormalizerForm])
 * 2) Conditional: lowercase the whole tag
 * 3) Conditional: remove all non-ASCII characters
 *
 * Normalization form used in step #1 and subsequent conditional steps depend on tag prefix.
 *
 * Process of splitting happens in [splitNormalizedTag] method.
 * Note that not all tags can be split, it depends on their prefix.
 *
 * Summarised per-prefix behavior:
 * +----------+------------+-----------+----------+-------+
 * | prefix   | norm. form | lowercase | no ASCII | split |
 * +----------+------------+-----------+----------+-------+
 * | @        | NFKC       | false     | false    | true  |
 * | ref_     | NFKC       | false     | false    | false |
 * | ~        | NFD        | false     | true     | true  |
 * | #        | NFD        | false     | true     | true  |
 * | sourceID | NFKC       | false     | false    | false |
 * | < ELSE > | NFD        | true      | true     | true  |
 * +----------+------------+-----------+----------+-------+
 *
 * By default, (if no special prefix is found) tag is normalized with NFD, lowercased, cleaned of non-ASCII and splittable.
 */
class TagNormalizer private constructor() {
    private data class TagProcessingPolicy(
        val normalizerForm: NormalizerForm,
        val removeNonAscii: Boolean,
        val lowercase: Boolean,
        val split: Boolean
    )

    companion object TagNormalizer_C {
        private val DEFAULT_POLICY =
            TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = true, split = true)
        private val PREFIX_TO_POLICY = mapOf(
            "@" to TagProcessingPolicy( NFKC, removeNonAscii = false, lowercase = false, split = true),
            "ref_" to TagProcessingPolicy( NFKC, removeNonAscii = false, lowercase = false, split = false),
            "sourceID" to TagProcessingPolicy( NFKC, removeNonAscii = false, lowercase = false, split = false),
            "~" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true),
            "#" to TagProcessingPolicy(NFD, removeNonAscii = true, lowercase = false, split = true)
        )

        private val AS_IS: CharArray = CharArray(128 - 32) { (it + 32).toChar() }
        private val TO_LOWER: CharArray = CharArray(128 - 32) { (it + 32).toChar().lowercaseChar() }

        /**
         * Main method for raw tag normalization. See[TagNormalizer] doc for more
         */
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
        fun splitNormalizedTag(normalizedTag: String): Pair<String, Any?> {
            if (!policyFor(normalizedTag).split) {
                return normalizedTag to null
            }
            val i = normalizedTag.indexOf('=')
            val key: String
            val value: Any?
            if (i > 1) {
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