package naksha.jbon

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

// TODO: Implement IDict
// TODO: Create a JbDictBuilder, that implements IDictBuilder and change JbEncoder to use an IDictBuilder for the local dictionary!
// TODO: We should improve the JbEncoder, so that it can better compress using global and local dictionaries.
// TODO: We should be able to detect not only strings, but as well objects in dictionaries.
// TODO: We need to add compression level to encoder, if high, we should try to insert whole objects into local dictionaries.
// TODO: We need a training mode, so that we can create an dictionary build, then use the encoder to try to insert all objects
//       into the global dictionary, count the number of times we find them, then eventually, reorder the global dictionary and
//       compact it, so that we get the best compression.

/**
 * A dictionary reader.
 * @constructor Create a new dictionary reader.
 */
@Suppress("DuplicatedCode", "OPT_IN_USAGE")
@JsExport
class JbDictionary : JbStructDecoder<JbDictionary>(), IDict {

    companion object JbDictionary_C {
        /**
         * The [PlatformType] of [JbDictionary].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JbDictionary::class).withPackageName(PACKAGE_NAME)
    }

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

    override fun stringAt(index: Int): String? {
        TODO("Not yet implemented")
    }

    override fun find(hash: Int): List<DictEntry> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary. This method will
     * as a side effect invoke [loadAll].
     * @return The index of the given string or -1.
     */
    override fun indexOf(string: String): Int {
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