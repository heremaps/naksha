@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An interface to be implemented by all `JBON` books.
 *
 * Books contain `JBON` units that can be referred to from a `JBON` binary. A `unit` in `JBON` is basically any defined value, please refer to the `JBON` specication. The purpose of books is deduplication and relocation of units. There are generally four type of books:
 * - `local` - embedded into a binary encoded `JBON`, small local compression.
 * - `global` - shared between applications and storages, strong compression factor.
 * - `member` - use to relocate units into dedicated places for indexing and searching.
 * - `const` - fixed constant units that encoder and decoder need to know.
 *
 * All books have two sections: strings and values. Even while values can be all units, except for strings, encoding primities does not make much sense, because it will rarly save any space. Generall the content are `JBON` structures, so one of the following:
 * - `JbonArray` - An array of arbitrary units.
 * - `JbonMap` - A map where the key can be any primitive, the value can be any unit.
 * - `JbonTagList` - A list of strings with some special encoding.
 * - `JbonObject` - A map where the key is a string literal and the value can be any unit.
 * - `JbonTagMap` - A map where the key is a string literal and the value can only be a primitive.
 * - `JbonDictionary` - A map where the key is a string literal and the value is a string.
 * - `JbonSpatial` - A byte-array that encodes an geometry encoded as [TWKB](https://github.com/TWKB/Specification/blob/master/twkb.md).
 * - `JbonBytes` - A byte-array with arbitrary content.
 * - `JbonBinary` - An byte-array with metadata about the content, optionally compressed by well known algorithms.
 *
 * All objects can be represented in `JSON` or `XML`, but only `JbonArray` and `JbonObject` are natural raw `JSON` entities. All others are custom extension to be used in Naksha context.
 *
 * Beware, the books represented by this interface contain these _HEAP_ representations of the data. So [IBook] represents a decoded `JBON` book. There is as well a binary representation as [ByteArray] that can be read using the `JbonDecoder` and that can be generated using the `JbonEncoder`. Storages need to support both formats, the binary [ByteArray] representation and the decoded _HEAP_ representation through [IBook].
 * @since 3.0.0
 * @see JbDictionary
 */
@JsExport
interface IBook {
    /**
     * The optional custom identifier of the book; if any.
     * @since 3.0.0
     */
    val id: String?

    /**
     * The amount of elements being in the dictionary.
     * @since 3.0.0
     */
    val length: Int

    /**
     * The book-type.
     * @since 3.0
     */
    val bookType: BookType

    /**
     * The database-number of the book, if this is a global book stored in a database.
     * @since 3.0
     */
    val databaseNumber: Long?

    /**
     * The feature-number of the book, if this is a global book stored in a database.
     * @since 3.0
     */
    val featureNumber: Long?

    /**
     * Returns the element at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the value being one of: `null`, `Boolean`, `Int`, `Long`, `Double`, `String`, `Map<String,Any?>`, or `List<Any?>`.
     * @since 3.0
     */
    @JsName("getByIndex")
    operator fun get(index: Int): Any?

    /**
     * Returns the value associated with the given name by looking up the index via [indexOfName] and then reading the value via [get]. Returns _null_ when the name is not found or the index maps to a _null_ slot.
     * @param name the member name to look up.
     * @return the value, or _null_ if the name is not present.
     * @since 3.0
     */
    @JsName("getByName")
    operator fun get(name: String): Any? {
        val i = indexOfName(name)
        return if (i < 0) null else get(i)
    }

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary.
     *
     * @param string the string to search.
     * @return the index of the given string or -1, if the string is not part of the dictionary.
     * @since 3.0.0
     */
    fun indexOfString(string: String): Int

    /**
     * Returns the string at the given index. If no such index exists, returns _null_.
     * @param index the index to query.
     * @return the string or _null_.
     * @since 3.0.0
     */
    fun getStringAt(index: Int): String?

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
    fun indexOfName(name: String): Int = -1

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
    fun getAllWithHash(hash: Int): List<DictEntry>
}
