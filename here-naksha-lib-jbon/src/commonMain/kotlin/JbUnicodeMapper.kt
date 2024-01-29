@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An intermediate helper to support parsing of unicode encoding in the internal format.
 */
@JsExport
abstract class JbUnicodeMapper<SELF : JbUnicodeMapper<SELF>> : JbObjectMapper<SELF>() {
    /**
     * Read the code-point at the current offset.
     * @param moveForward If true, the offset will be adjusted after reading the code point; false otherwise.
     * @return The unicode of the code-point at the current offset.
     */
    internal fun readCodePoint(moveForward: Boolean): Int {
        val view = reader.view()
        if (reader.offset >= encodingEnd) return -1
        var unicode = view.getInt8(reader.offset).toInt() and 0xff
        if (unicode < 128) {
            if (moveForward) {
                reader.offset += 1
            }
            return unicode
        }
        // Multibyte encoding
        if (unicode ushr 6 == 0b10) {
            // Two byte encoding
            check(reader.offset + 1 < encodingEnd)
            unicode = ((unicode and 0b111111) shl 8) or view.getInt8(reader.offset + 1).toInt() and 0xff
            if (moveForward) {
                reader.offset += 2
            }
            return unicode
        }
        // Three byte encoding
        check(reader.offset + 2 < encodingEnd)
        unicode = ((unicode and 0b111111) shl 16) or view.getInt16(reader.offset + 1).toInt() and 0xffff
        if (moveForward) {
            reader.offset += 3
        }
        return unicode
    }

    /**
     * Assumes that the [offset] is located at a code-point or string-reference. Returns either the size of the code point
     * in byte (1 to 3) or 0, if a string-reference is found.
     * @return 1 to 3 when a unicode code-point of that size is found; 0 when a string-reference is found; -1 for EOF.
     */
    private fun unitUnicodeSize() : Int {
        val view = reader.view()
        val offset = reader.offset
        if (offset < 0 || offset >= view.getSize()) return EOF
        return when(val byteValue = (view.getInt8(offset).toInt() and 0b0000_0000)) {
            else -> EOF
        }
        TODO("Finish me")
    }
}