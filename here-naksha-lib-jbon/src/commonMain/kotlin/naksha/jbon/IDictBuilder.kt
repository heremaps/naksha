package naksha.jbon

/**
 * An interface of a mutable in-memory dictionary.
 * @since 3.0.0
 */
interface IDictBuilder : IDict {
    /**
     * Append the given value to the end of the dictionary. If the value is already in the dictionary, return the index.
     * @param value the value to add, must be any of `null`, `Boolean`, `Int`, `Int64`, `Double`, `String`, `Map<String,Any?>`, or `List<Any?>`.
     * @return the index of the value (this can differ from [length], if the value is already in the dictionary).
     * @since 3.0.0
     */
    fun add(value: Any?): Int

    /**
     * Compact the dictionary into a binary form.
     * @param id the optional identifier of the dictionary.
     * @return the JBON encoded dictionary.
     */
    fun toJBON(id: String?): ByteArray
}