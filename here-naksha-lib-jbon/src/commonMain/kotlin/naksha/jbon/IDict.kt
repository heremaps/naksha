@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import kotlin.js.JsExport

/**
 * An interface to be implemented by all dictionaries. Dictionaries can contain a combination of the following types, and only of these:
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
@JsExport
interface IDict {
    /**
     * The identifier of the dictionary; if any.
     * @since 3.0.0
     */
    val id: String?

    /**
     * The amount of elements being in the dictionary.
     * @since 3.0.0
     */
    val length: Int

    /**
     * Returns the element at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the value being one of: `null`, `Boolean`, `Int`, `Int64`, `Double`, `String`, `Map<String,Any?>`, or `List<Any?>`.
     * @since 3.0.0
     */
    fun get(index: Int): Any?

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary.
     *
     * @param string the string to search.
     * @return the index of the given string or -1, if the string is not part of the dictionary.
     * @since 3.0.0
     */
    fun indexOf(string: String): Int

    /**
     * Returns the string at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the string or _null_.
     * @since 3.0.0
     */
    fun stringAt(index: Int): String?

    /**
     * Returns `true` if this dictionary contains a `memberNames` section — a parallel array
     * of string names that give each slot a symbolic name used for member-reference resolution.
     * @since 3.0.0
     */
    fun hasNames(): Boolean = false

    /**
     * Returns the index of the given name in the `memberNames` section, or `-1` if not found
     * or if [hasNames] returns `false`.
     * @param name the name to look up.
     * @return the index, or `-1`.
     * @since 3.0.0
     */
    fun getIndexOf(name: String): Int = -1

    /**
     * Returns the name at the given index from the `memberNames` section, or `null` if no
     * such index exists or if [hasNames] returns `false`.
     * @param index the index to query.
     * @return the name string, or `null`.
     * @since 3.0.0
     */
    fun getNameAt(index: Int): String? = null

    /**
     * Returns the number of entries in the `memberNames` section. Returns `0` if
     * [hasNames] returns `false`.
     * @since 3.0.0
     */
    fun namesLength(): Int = 0

    /**
     * Find all entries in the dictionary that have the given hash.
     * @param hash the hash to find.
     * @return a list of all entries that match the given hash.
     * @since 3.0.0
     */
    fun find(hash: Int): List<DictEntry>
}