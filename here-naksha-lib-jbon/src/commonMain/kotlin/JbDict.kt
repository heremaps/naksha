@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A dictionary reader.
 */
@Suppress("DuplicatedCode")
@JsExport
class JbDict : JbObjectMapper<JbDict>() {
    /**
     * Cached ID of the dictionary, if any.
     */
    private var id: String? = null

    /**
     * The cached length of the dictionary, set only after all entries have been read.
     */
    private var length: Int = -1

    /**
     * All strings being part of the dictionary by index.
     */
    private val content = ArrayList<String>()

    /**
     * The offset of the strings by index.
     */
    private val indexToOffset = ArrayList<Int>()

    /**
     * Returns the identifier of the dictionary.
     * @return The identifier of the dictionary, if any.
     */
    fun id(): String? {
        return id
    }

    override fun parseHeader(mandatory: Boolean) {
        val type = type()
        check(type == TYPE_GLOBAL_DICTIONARY || type == TYPE_LOCAL_DICTIONARY)
        addOffset(1)
        check(isInt())
        val size = readInt32()
        check(next())
        if (type == TYPE_GLOBAL_DICTIONARY) {
            check(isString())
            id = readString().toString()
            check(next())
        }
        setContentSize(size)
    }

    override fun clear(): JbDict {
        super.clear()
        id = null
        length = -1
        if (content.size > 0) {
            content.clear()
        }
        if (indexToOffset.size > 0) {
            indexToOffset.clear()
        }
        return this
    }

    override fun reset(): JbDict {
        super.reset()
        length = -1
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
        if (length < 0) {
            val content = this.content
            val indexToOffset = this.indexToOffset
            var len = content.size
            while (len++ < index && isString()) {
                val string = readString().toString()
                content.add(string)
                indexToOffset.add(offset())
                next()
            }
            length = content.size
        }
    }

    /**
     * Loads all strings of the dictionary and index them.
     * @return this.
     * @throws IllegalStateException If the view is invalid.
     */
    fun loadAll(): JbDict {
        ensure(Int.MAX_VALUE)
        return this
    }

    /**
     * Returns the strings in the dictionary. The method is only precise after [loadAll] was invoked.
     * @return The current amount of strings cached.
     */
    fun length(): Int {
        return length
    }

    /**
     * Returns the string from the given index.
     * @return The string.
     */
    fun get(index: Int): String {
        ensure(index)
        val content = this.content
        require(index >= 0 && index < content.size)
        return content[index]
    }

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary. This method will
     * as a side effect invoke [loadAll].
     * @return The index of the given string or -1.
     */
    fun indexOf(string: String): Int {
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