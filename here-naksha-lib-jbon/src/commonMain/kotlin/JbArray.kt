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
            val view = reader.useView()
            val leadIn = view.getInt8(reader.offset).toInt() and 0xff
            check((leadIn and 0xf0) == TYPE_CONTAINER)
            check((leadIn and 0b0000_1100) == TYPE_CONTAINER_ARRAY)
            val sizeIndicator = leadIn and 0b11
            val size: Int
            when (sizeIndicator) {
                0 -> {
                    size = 1
                    reader.addOffset(1)
                }

                1 -> {
                    size = 2 + view.getInt8(reader.offset + 1).toInt() and 0xff
                    reader.addOffset(2)
                }

                2 -> {
                    size = 3 + view.getInt16(reader.offset + 1).toInt() and 0xffff
                    reader.addOffset(3)
                }

                3 -> {
                    size = 5 + view.getInt32(reader.offset + 1)
                    reader.addOffset(5)
                }

                else -> {
                    throw IllegalStateException()
                }
            }
            setContentSize(size)
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