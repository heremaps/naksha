@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * Implementation-neutral classification of identifier text. Classification does not limit UTF-8 length, except that reserved names have an explicit 32-byte limit.
 *
 * Acceptance is cumulative in this order: [ALPHABET_ONLY], [SAFE], [URL_SAFE], [UNSAFE], [INTERNAL], [RESERVED]. This is an acceptance policy, not literal inclusion of character patterns. [ILLEGAL] is never accepted. Classification does not grant permission to access or modify an entity: custom names must not be [INTERNAL], and callers must separately protect [RESERVED] entities from user creation and modification.
 * @since 3.0
 */
@JsExport
class IdTextType : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = IdTextType::class

    override fun initClass() {}

    /**
     * The classification value: 0..5 for legal types, or 127 for [ILLEGAL].
     * @since 3.0
     */
    val intValue: Int
        get() = value as Int

    /**
     * Tests whether this expected type accepts [textType]. Illegal or undefined classifications are never accepted.
     * @since 3.0
     */
    fun accepts(textType: IdTextType): Boolean =
        isDefined && textType.isDefined && this !== ILLEGAL && textType !== ILLEGAL && textType.intValue <= intValue

    companion object IdTextType_C {
        /**
         * Nonempty lowercase ASCII letters only, `a..z` (value 0).
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val ALPHABET_ONLY = def(IdTextType::class, 0)

        /**
         * Lowercase ASCII letters, digits and underscore, without a leading digit (value 1). This describes text syntax, not implementation-specific keywords.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val SAFE = def(IdTextType::class, 1)

        /**
         * RFC 3986 unreserved ASCII characters: letters, digits, underscore, hyphen, period and tilde; leading digits are allowed (value 2).
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val URL_SAFE = def(IdTextType::class, 2)

        /**
         * Other legal text, including spaces, punctuation and well-formed Unicode (value 3). Quotes, control characters, malformed Unicode, and invalid internal/reserved names are excluded. Implementations may require quoting or aliasing this text.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val UNSAFE = def(IdTextType::class, 3)

        /**
         * A name containing at least one dollar sign and matching `[a-z][a-z0-9_$]*` (value 4). Reserved for trusted internal naming, including aliases; not permitted as a custom name.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val INTERNAL = def(IdTextType::class, 4)

        /**
         * A name matching `naksha~[a-z0-9_]{1,25}`, at most 32 ASCII/UTF-8 bytes (value 5). Users may reference reserved entities, but creation and modification require separate authorization.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val RESERVED = def(IdTextType::class, 5)

        /**
         * Empty text; single/double quotes; Unicode control characters; malformed UTF-16; invalid dollar-sign names; or any other name beginning with `naksha~` (value 127). Alias requests reject this classification with [NakshaError.ILLEGAL_ID].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val ILLEGAL = def(IdTextType::class, 127)

        /**
         * Classifies text without changing its spelling, case or Unicode normalization. Illegal content takes precedence over internal and reserved patterns.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun classify(text: String): IdTextType {
            if (text.isEmpty()) return ILLEGAL
            var type = 0
            var hasDollar = false
            var internal = text[0] in 'a'..'z'
            val reserved = text.startsWith("naksha~")
            var validReserved = text.length in 8..32
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\'' || c == '"' || c.code <= 0x1f || c.code in 0x7f..0x9f) return ILLEGAL
                if (c in '\ud800'..'\udbff') {
                    if (i + 1 >= text.length || text[i + 1] !in '\udc00'..'\udfff') return ILLEGAL
                    type = 3
                    internal = false
                    validReserved = false
                    i += 2
                    continue
                }
                if (c in '\udc00'..'\udfff') return ILLEGAL
                val lower = c in 'a'..'z'
                val digit = c in '0'..'9'
                if (!(lower || digit || c == '_' || c == '$')) internal = false
                if (i >= 7 && !(lower || digit || c == '_')) validReserved = false
                if (c == '$') hasDollar = true
                val characterType = when {
                    lower -> 0
                    c in 'A'..'Z' -> 2
                    c == '_' -> 1
                    digit -> if (i == 0) 2 else 1
                    c == '-' || c == '.' || c == '~' -> 2
                    else -> 3
                }
                if (characterType > type) type = characterType
                i++
            }
            if (reserved) return if (validReserved) RESERVED else ILLEGAL
            if (hasDollar) return if (internal) INTERNAL else ILLEGAL
            return when (type) {
                0 -> ALPHABET_ONLY
                1 -> SAFE
                2 -> URL_SAFE
                else -> UNSAFE
            }
        }
    }
}
