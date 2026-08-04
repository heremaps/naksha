package naksha.base

expect class Literal private constructor(string: String): CharSequence {
    companion object Literal_C {
        /**
         * Returns the cached string singleton for the given string or the normalized string.
         *
         * This method does not intern the given string, but when there is an interned literal, it returns it.
         * @param string the string.
         * @return the string in [NFC](https://www.unicode.org/reports/tr15/) form, if available as singleton, otherwise a dedicated new normalized instance.
         * @since 3.0
         * @see Base.normalize
         */
        fun normalize(string: String): String

        /**
         * Returns the cached literal singleton for the given string or `null`, if the string is not yet cached as literal.
         *
         * @param nfcString the string in [NFC](https://www.unicode.org/reports/tr15/) form.
         * @return the cached literal or `null`, if not yet cached as literal.
         * @since 3.0
         */
        fun find(nfcString: String): Literal?

        /**
         * Returns the literal singleton for the given string.
         *
         * @param string the string.
         * @return the literal.
         * @since 3.0
         * @see literal
         */
        fun of(string: String): Literal

        /**
         * Returns the literal singleton for the given string, which must be already in [NFC](https://www.unicode.org/reports/tr15/) form. The method does not verify this for the sake of performance. Beware, providing a string that is not normalized can cause huge problems. Avoid this method, unless you are sure the string is in [NFC](https://www.unicode.org/reports/tr15/) form.
         *
         * @param nfcString the string in [NFC](https://www.unicode.org/reports/tr15/) form.
         * @return the literal.
         * @since 3.0
         */
        fun ofNfcString(nfcString: String): Literal

        /**
         * Returns the literal singleton for the given string.
         *
         * The same as [of], but very good for static import, so that it can be used like `literal("foo")`.
         *
         * @param string the string.
         * @return the literal.
         * @since 3.0
         * @see of
         */
        fun literal(string: String): Literal
    }

    /**
     * The weak-reference to this literal.
     * @since 3.0
     */
    val weakRef: LiteralWeakRef

    /**
     * The string backing the literal.
     * @since 3.0
     */
    val string: String

    override val length: Int
    override fun get(index: Int): Char
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence
}