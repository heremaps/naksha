@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbDictionary(override val bookType: BookType = BookType.LOCAL_BOOK,) : JbStructDecoder<JbDictionary>(), IBook {
    override var databaseNumber: Int64? = null
    override var featureNumber: Int64? = null

    /**
     * Cached ID of the dictionary, if any.
     */
    override var id: String? = null

    /**
     * The cached length of the dictionary, set only after all entries have been read.
     */
    private var _length: Int = -1

    /**
     * All strings being part of the dictionary by index.
     */
    private val content = ArrayList<String>()

    /**
     * The offset of the strings by index.
     */
    private val indexToOffset = ArrayList<Int>()

    override fun onMap() {}

    override fun doParseHeader() {
        id = if (reader.isString()) reader.decodeString() else null
        reader.nextUnit()
    }

    override fun clear(): JbDictionary {
        super.clear()
        id = null
        _length = -1
        if (content.size > 0) {
            content.clear()
        }
        if (indexToOffset.size > 0) {
            indexToOffset.clear()
        }
        return this
    }

    override fun reset(): JbDictionary {
        super.reset()
        _length = -1
        if (content.size > 0) {
            content.clear()
        }
        if (indexToOffset.size > 0) {
            indexToOffset.clear()
        }
        return this
    }

    /**
     * Internally called to ensure that the string at the given index is loaded, if such an index exists.
     * @param index The index to ensure, if possible.
     */
    private fun ensure(index: Int) {
        if (this._length < 0) {
            // We have not yet loaded all strings.
            val content = this.content
            val indexToOffset = this.indexToOffset
            var length = content.size
            while (length <= index && reader.isString()) {
                val string = reader.decodeString()
                content.add(string)
                indexToOffset.add(reader.pos)
                length++
                reader.nextUnit()
            }
            check(length == content.size)
            // If nothing left
            if (!reader.isString()) {
                this._length = length
            }
        }
    }

    /**
     * Loads all strings of the dictionary and index them.
     * @return this.
     * @throws IllegalStateException If the view is invalid.
     */
    fun loadAll(): JbDictionary {
        ensure(Int.MAX_VALUE)
        return this
    }

    /**
     * Returns the strings in the dictionary. The method is only precise after [loadAll] was invoked.
     * @return The current amount of strings cached; -1 if the length is yet unknown an [loadAll] need to invoked first.
     */
    override val length: Int
        get() = _length

    /**
     * Returns the string from the given index.
     * @return The string.
     */
    override fun get(index: Int): String {
        ensure(index)
        val content = this.content
        require(index >= 0 && index < content.size)
        return content[index]
    }

    override fun getStringAt(index: Int): String? {
        TODO("Not yet implemented")
    }

    override fun getAllWithHash(hash: Int): List<DictEntry> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary. This method will
     * as a side effect invoke [loadAll].
     * @return The index of the given string or -1.
     */
    override fun indexOfString(string: String): Int {
        loadAll()
        val content = this.content
        val length = content.size
        var i = 0
        while (i < length) {
            if (content[i] == string) {
                return i
            }
            i++
        }
        return -1
    }
}