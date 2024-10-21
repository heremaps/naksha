package naksha.jbon

/**
 * An interface to be implemented by all in-memory dictionaries. Dictionaries can contain a combination of the following types, and only of these:
 * - `null`
 * - `Boolean`
 * - `Int`
 * - `Int64`
 * - `Double`
 * - `String`
 * - `Map<String,Any?>` - with _Any_ again being limited to these types.
 * - `List<Any?>` - with _Any_ again being limited to these types.
 * @since 3.0.0
 * @see JbDictionary
 */
interface IDict {
    /**
     * The amount of elements being in the dictionary.
     */
    val length: Int

    /**
     * Returns the element at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the value being one of: `null`, `Boolean`, `Int`, `Int64`, `Double`, `String`, `Map<String,Any?>`, or `List<Any?>`.
     */
    fun get(index: Int): Any?

    /**
     * Returns the string at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the string or _null_.
     */
    fun stringAt(index: Int): String?

    /**
     * Find all entries in the root of the dictionary that have the given hash.
     * @param hash the hash to find.
     * @return a list of all value pairs that match the given hash.
     */
    fun find(hash: Int): List<Pair<Int, Any>>?
}