@file:OptIn(ExperimentalJsExport::class, ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.base

import naksha.base.NakshaConst.IdConst_C.MAX_ID_LENGTH
import naksha.base.NakshaConst.IdConst_C.MAX_INTERNAL_ID_LENGTH
import naksha.base.Base.BaseCompanion.fal
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Helper class to verify identifier.
 * @since 3.0
 */
@JsExport
class IdVerifier private constructor(
    /** The characters allowed as first character. */
    private var start: Map<Char, Boolean>?,
    private var startInternal: Map<Char, Boolean>?,
    /** The characters allowed at the rest of the identifier. */
    private var chars: Map<Char, Boolean>?,
    private var charsInternal: Map<Char, Boolean>?,
    /** The maximum length of the identifier. */
    private var maxLength: Int?,
    /** Human-readable information for error messages which characters are allowed. */
    private var rangeInfo: String?,
    private var rangeInfoInternal: String?
) {

    /**
     * Tests if the given identifier is a valid identifier of this kind.
     *
     * @param id the identifier to be tested.
     * @param internal if the internal variant of the identifier is to be tested.
     * @return the given identifier, if it is valid; otherwise throws an exception.
     * @throws NakshaException with [ILLEGAL_ID][NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is `true` and the identifier is not valid.
     * @since 3.0
     * @see isValidId
     */
    @JvmOverloads
    fun verify(id: String?, internal: Boolean = false): String {
        // n=3 because:
        // 3 = caller of this function
        // 2 = caller of isValidId (this function)
        // 1 = caller of fal
        // 0 = current function, would be `fal` itself
        isValidId(id, internal, throwOnError = true, 3)
        return id!!
    }

    /**
     * Tests if the given identifier is a valid identifier of this kind.
     *
     * @param id the identifier to test.
     * @param internal if the internal variant of the identifier is to be tested.
     * @param throwOnError if an exception should be thrown, when the verification failed.
     * @param n if an exception is throw, the amount of frames to backtrace to add filename and line; defaults to `2`.
     * @return _true_ if the identifier is valid; _false_ otherwise.
     * @throws NakshaException with [ILLEGAL_ID][NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is `true` and the identifier is not valid.
     * @since 3.0
     * @see [verify]
     */
    @JvmOverloads
    fun isValidId(id: String?, internal: Boolean = false, throwOnError: Boolean = false, n: Int = 2): Boolean {
        if (id.isNullOrEmpty()) {
            if (throwOnError) throw illegalId("${fal(n)}The given identifier is null or empty")
            else return false
        }
        if (id == "naksha") {
            if (internal) return true
            if (throwOnError) throw illegalId("${fal(n)}The identifier 'naksha' is forbidden")
            else return false
        }
        val maxLength = this.maxLength
        if (maxLength != null && id.length > maxLength) {
            if (throwOnError) throw illegalId("${fal(n)}The identifier '$id' is too long: ${id.length}, must be maximal $maxLength")
            return false
        }
        val rangeInfo = if (internal) this.rangeInfoInternal else this.rangeInfo
        var i = 0
        var c = id[i++]
        val start = if (internal) this.startInternal else this.start
        if (start != null && !start.containsKey(c)) {
            if (throwOnError) throw illegalId("${fal(n)}The first character of identifier '$id' must be $rangeInfo, but was $c")
            else return false
        }
        val chars = if (internal) this.charsInternal else this.chars
        if (chars != null) {
            while (i < id.length) {
                c = id[i++]
                if (!chars.containsKey(c)) {
                    if (throwOnError) throw illegalId("${fal(n)}Invalid character in identifier '$id' at index $i: '$c', expected $rangeInfo")
                    else return false
                }
            }
        }
        return true
    }

    companion object TextVerifier_C {
        /**
         * A verifier that allows all characters:
         * - standard: `.*`
         * - internal: `.*`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val ANY = IdVerifier(
            start = null,
            startInternal = null,
            chars = null,
            charsInternal = null,
            maxLength = null,
            rangeInfo = ".*",
            rangeInfoInternal = ".*"
        )

        /**
         * A verifier that allows only 53-bit positive numbers:
         * - standard: `[1-9][0-9]{15}`
         * - internal: `[1-9][0-9]{15}`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TRANSACTION = IdVerifier(
            start = firstCharOfUint53(),
            startInternal = firstCharOfUint53(),
            chars = charsOfUint53(),
            charsInternal = charsOfUint53(),
            maxLength = 16,
            rangeInfo = "[1-9][0-9]{15}",
            rangeInfoInternal = "[1-9][0-9]{15}"
        )

        /**
         * A verifier that allows strict characters only:
         * - standard: `[a-z][a-z0-9_]`
         * - internal: `[a-z_][a-z0-9_$~]`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val DATABASE_AND_STORAGE = IdVerifier(
            start = firstChar(false),
            startInternal = firstChar(true),
            chars = charsOfStrict(false),
            charsInternal = charsOfStrict(true),
            maxLength = MAX_ID_LENGTH,
            rangeInfo = "[a-z][a-z0-9_]{${MAX_ID_LENGTH-1}}",
            rangeInfoInternal = "[a-z_][a-z0-9_$~]{${MAX_ID_LENGTH-1}}"
        )

        /**
         * A verifier that allows strict characters only:
         * - standard: `[a-z][a-z0-9_]`
         * - internal: `[a-z_][a-z0-9_]`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MEMBER_AND_INDEX = IdVerifier(
            start = firstChar(false),
            startInternal = firstChar(true),
            chars = charsOfStrict(false),
            charsInternal = charsOfStrict(false),
            maxLength = MAX_ID_LENGTH,
            rangeInfo = "[a-z][a-z0-9_]{${MAX_ID_LENGTH-1}}",
            rangeInfoInternal = "[a-z_][a-z0-9_]{${MAX_ID_LENGTH-1}}"
        )

        /**
         * A verifier that allows relaxed characters:
         * - standard: `[a-z][a-z0-9_-:]`
         * - internal: `[a-z_][a-z0-9_-:$~]`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CATALOG_AND_COLLETION = IdVerifier(
            start = firstChar(false),
            startInternal = firstChar(true),
            chars = charsOfRelaxed(false),
            charsInternal = charsOfRelaxed(true),
            maxLength = MAX_ID_LENGTH,
            rangeInfo = "[a-z][a-z0-9_-:]{${MAX_ID_LENGTH - 1}}",
            rangeInfoInternal = "[a-z_][a-z0-9_-:$~]{${MAX_INTERNAL_ID_LENGTH - 1}}"
        )

        // ── Character-set builders ────────────────────────────────────────────

        private fun firstCharOfUint53(): Map<Char, Boolean> {
            val map = mutableMapOf<Char, Boolean>()
            for (c in '1'..'9') map[c] = true
            return map.toMap()
        }

        private fun charsOfUint53(): Map<Char, Boolean> {
            val map = mutableMapOf<Char, Boolean>()
            for (c in '0'..'9') map[c] = true
            return map.toMap()
        }

        private fun firstChar(internal: Boolean): Map<Char, Boolean> {
            val map = mutableMapOf<Char, Boolean>()
            for (c in 'a'..'z') map[c] = true
            if (internal) map['_'] = true
            return map.toMap()
        }

        private fun charsOfStrict(internal: Boolean): Map<Char, Boolean> {
            val map = mutableMapOf<Char, Boolean>()
            for (c in 'a'..'z') map[c] = true
            for (c in '0'..'9') map[c] = true
            map['_'] = true
            if (internal) {
                map['~'] = true
                map['$'] = true
            }
            return map.toMap()
        }

        private fun charsOfRelaxed(internal: Boolean): Map<Char, Boolean> {
            val map = mutableMapOf<Char, Boolean>()
            for (c in 'a'..'z') map[c] = true
            for (c in '0'..'9') map[c] = true
            map['_'] = true
            map['-'] = true
            map[':'] = true
            if (internal) {
                map['~'] = true
                map['$'] = true
            }
            return map.toMap()
        }
    }
}