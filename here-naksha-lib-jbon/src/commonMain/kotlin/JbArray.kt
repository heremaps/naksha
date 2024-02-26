@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An array implementation.
 */
@JsExport
class JbArray : JbEntryArray<JbArray>() {
    override fun parseHeader(mandatory: Boolean) {
        if (mandatory) {
            check(reader.unitType() == TYPE_ARRAY)
            val unitSize = reader.unitSize()
            check(reader.enterUnit())
            setContentSize(unitSize)
        }
        index = -1
        length = if (contentSize() == 0) 0 else Int.MAX_VALUE
    }

    override fun nextEntry(): Boolean {
        if (reader.offset < encodingEnd) {
            reader.nextUnit()
            return reader.offset < encodingEnd
        }
        return false
    }

    override fun loadEntry() {
    }

    override fun dropEntry() {
    }

    /**
     * Returns the reader, being positioned at the value of the entry.
     * @return The reader, positioned at the value of the entry.
     * @throws IllegalStateException If the position is invalid.
     */
    fun value(): JbReader {
        check(index >= 0)
        return reader
    }

}