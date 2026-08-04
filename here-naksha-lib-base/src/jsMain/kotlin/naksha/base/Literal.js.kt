package naksha.base

@JsExport
actual class Literal private actual constructor(string: String): CharSequence {
    actual override val length: Int = string.length
    @Suppress("NON_EXPORTABLE_TYPE")
    actual override operator fun get(index: Int): Char = string[index]
    @Suppress("NON_EXPORTABLE_TYPE")
    actual override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = string.subSequence(startIndex, endIndex)
    // ---------------------------< END OF CharSequence >------ ------------------

    actual val weakRef: LiteralWeakRef = LiteralWeakRef(this)
    actual val string: String = string
    override fun toString(): String = string
    override fun hashCode(): Int = string.hashCode()
    // This is the main trick, equals does work by reference compare for literals!
    override fun equals(other: Any?): Boolean = this === other

    actual companion object Literal_C {
        private val cache = hashMapOf<String, LiteralWeakRef>()

        private fun toNfc(string: String): String = string.asDynamic().normalize("NFC").unsafeCast<String>()

        @JsStatic
        actual fun normalize(string: String): String {
            val s = toNfc(string)
            val literal = cache[s]?.get()
            return literal?.string ?: s
        }

        @JsStatic
        actual fun find(nfcString: String): Literal? = cache[nfcString]?.get()

        @JsStatic
        actual fun of(string: String): Literal {
            val s = toNfc(string)
            var literal = cache[s]?.get()
            if (literal == null) {
                literal = Literal(s)
                cache[s] = literal.weakRef
            }
            return literal
        }

        @JsStatic
        actual fun ofNfcString(nfcString: String): Literal {
            var literal = cache[nfcString]?.get()
            if (literal == null) {
                literal = Literal(nfcString)
                cache[nfcString] = literal.weakRef
            }
            return literal
        }

        @JsStatic
        actual fun literal(string: String): Literal = of(string)

        /**
         * Garbage collect the literal cache.
         *
         * Can be called from a timer regularly within the browser.
         * @since 3.0
         */
        @JsStatic
        fun gc() {
            cache.forEach { (k, v) -> if (v.get() == null) cache.remove(k) }
        }
    }
}