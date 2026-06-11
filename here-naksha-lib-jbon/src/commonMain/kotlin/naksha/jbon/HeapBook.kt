@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import kotlin.js.JsExport

/**
 * A mutable [IBook] implementation on the Java _HEAP_.
 * @since 3.0.0
 */
@JsExport
class HeapBook : IBook {
    private val _names = mutableListOf<String>()
    private val _values = mutableListOf<Any?>()
    private val _nameIndex = mutableMapOf<String, Int>()

    override var id: String? = null

    override val length: Int
        get() = _values.size

    override fun get(index: Int): Any? = _values.getOrNull(index)

    override fun indexOf(string: String): Int = _nameIndex[string] ?: -1

    override fun stringAt(index: Int): String? {
        val name = _names.getOrNull(index)
        if (name != null) return name
        return _values.getOrNull(index) as? String
    }

    override fun hasNames(): Boolean = true

    override fun getIndexOf(name: String): Int = _nameIndex[name] ?: -1

    override fun getNameAt(index: Int): String? = _names.getOrNull(index)

    override fun namesLength(): Int = _names.size

    override fun getByName(name: String): Any? {
        val i = _nameIndex[name] ?: return null
        return _values.getOrNull(i)
    }

    override fun find(hash: Int): List<DictEntry> = emptyList()

    /**
     * Creates a shallow copy of this dictionary.
     * @return a new [HeapBook] with the same entries.
     * @since 3.0.0
     */
    fun copy(): HeapBook {
        val c = HeapBook()
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
