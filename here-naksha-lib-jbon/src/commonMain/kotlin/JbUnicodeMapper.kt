@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An intermediate helper to support parsing of unicode encoding in the internal format.
 */
@JsExport
abstract class JbUnicodeMapper<SELF : JbUnicodeMapper<SELF>> : JbStruct<SELF>() {
    /**
     * Read the code-point at the current offset.
     * @param moveForward If true, the offset will be adjusted after reading the code point; false otherwise.
     * @return The unicode of the code-point at the current offset.
     */
    internal fun readCodePoint(moveForward: Boolean): Int {
        val view = reader.view()
        if (reader.offset() >= end) return -1
        var unicode = view.getInt8(reader.offset()).toInt() and 0xff
        if (unicode < 128) {
            if (moveForward) reader.addOffset(1)
            return unicode
        }
        // Multibyte encoding
        if (unicode ushr 6 == 0b10) {
            // Two byte encoding
            check(reader.offset() + 1 < end)
            unicode = ((unicode and 0b111111) shl 8) or view.getInt8(reader.offset() + 1).toInt() and 0xff
            if (moveForward) reader.addOffset(2)
            return unicode
        }
        // Three byte encoding
        check(reader.offset() + 2 < end)
        unicode = ((unicode and 0b111111) shl 16) or view.getInt16(reader.offset() + 1).toInt() and 0xffff
        if (moveForward) reader.addOffset(3)
        return unicode
    }
}