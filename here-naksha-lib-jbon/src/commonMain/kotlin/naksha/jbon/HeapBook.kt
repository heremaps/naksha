@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A mutable [IBook] implementation on the Java _HEAP_.
 * @since 3.0.0
 */
@JsExport
class HeapBook(
    override var bookType: BookType
) : IBook {
    companion object HeapBook_C {
        /**
         * Creates a copy of the given book.
         * @param other The [IBook] to make a copy of.
         * @param databaseNumber The database-number of the copy, if different.
         * @param featureNumber The feature-number of the copy, if different.
         * @return a new [HeapBook] with the same entries.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun copyOf(other: IBook, databaseNumber: Long? = other.databaseNumber, featureNumber: Long? = other.featureNumber): HeapBook {
            val c = HeapBook(other.bookType)
            c.id = other.id
            c.databaseNumber = other.databaseNumber
            c.featureNumber = other.featureNumber
            if (other is HeapBook) {
                c._names.addAll(other._names)
                c._values.addAll(other._values)
                for ((name,index) in other._nameIndex) c._nameIndex[name] = index
            } else {
                val namesLen = other.namesLength()
                for (i in 0..<namesLen) {
                    val name = other.getNameAt(i)!!
                    val value = other[name]
                    c.put(name, value)
                }
            }
            return c
        }


    }

    override var databaseNumber: Long? = null
    override var featureNumber: Long? = null

    private val _names = mutableListOf<String>()
    private val _values = mutableListOf<Any?>()
    private val _nameIndex = mutableMapOf<String, Int>()

    override var id: String? = null

    override val length: Int
        get() = _values.size

    override fun get(index: Int): Any? = _values.getOrNull(index)

    override fun indexOfString(string: String): Int = _nameIndex[string] ?: -1

    override fun getStringAt(index: Int): String? {
        val name = _names.getOrNull(index)
        if (name != null) return name
        return _values.getOrNull(index) as? String
    }

    override fun hasNames(): Boolean = true

    override fun indexOfName(name: String): Int = _nameIndex[name] ?: -1

    override fun getNameAt(index: Int): String? = _names.getOrNull(index)

    override fun namesLength(): Int = _names.size

    override fun get(name: String): Any? {
        val i = _nameIndex[name] ?: return null
        return _values.getOrNull(i)
    }

    override fun getAllWithHash(hash: Int): List<DictEntry> = emptyList()

    /**
     * Creates a shallow copy of this dictionary.
     * @param bookType The [BookType] of the copy, if different.
     * @param databaseNumber The database-number of the copy, if different.
     * @param featureNumber The feature-number of the copy, if different.
     * @return a new [HeapBook] with the same entries.
     * @since 3.0.0
     */
    @JvmOverloads
    fun copy(bookType: BookType = this.bookType, databaseNumber: Long? = this.databaseNumber, featureNumber: Long? = this.featureNumber): HeapBook {
        val c = HeapBook(bookType)
        c.databaseNumber = this.databaseNumber
        c.featureNumber = this.featureNumber
        for (i in _names.indices) {
            c.put(_names[i], _values[i])
        }
        return c
    }

    /**
     * Put a value into the dictionary, auto-assigning an index.
     * If the name already exists, the value is updated in-place.
     * @param name the member name.
     * @param value the value.
     * @return The index at which the value was placed.
     * @since 3.0.0
     */
    fun put(name: String, value: Any?): Int {
        val index = _nameIndex[name]
        if (index != null) {
            _values[index] = value
            return index
        }
        val idx = _names.size
        _names.add(name)
        _nameIndex[name] = idx
        _values.add(value)
        return idx
    }
}
