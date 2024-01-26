@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Offers a dictionary wrapper.
 */
@JsExport
class JbDict(val view: IDataView) {
    private val reader = JbReader(view)

    // id of the dictionary
    val id: String

    // All strings being part of the dictionary by index
    private val content = ArrayList<String>()

    // Reuse our own reader.
    init {
        require(TYPE_DICTIONARY == reader.type())
        reader.pos++
        // Ignore the size of the dictionary, not important for us.
        check(reader.isInt())
        check(reader.next())
        // Read the id
        check(reader.isString())
        val stringReader = JbString(reader)
        id = stringReader.toString()
        // Read content
        while (reader.next()) {
            check(reader.isString())
            stringReader.map(reader)
            content.add(stringReader.toString())
        }
    }

    /**
     * Returns the strings in the dictionary.
     * @return The amount of string being in the dictionary.
     */
    fun length(): Int {
        return content.size
    }

    /**
     * Returns the string from the given index.
     * @return The string.
     */
    fun get(index : Int) : String {
        require(index >= 0 && index < content.size)
        return content[index]
    }

    /**
     * Returns the index of the given string or -1, if the string is not part of the dictionary.
     * @return The index of the given string or -1.
     */
    fun indexOf(string : String) : Int {
        val content = this.content
        val size = content.size
        var i = 0
        while (i < size) {
            if (content[i] == string) {
                return i
            }
            i++
        }
        return -1
    }
}