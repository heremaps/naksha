@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A string reader.
 */
@JsExport
class JbString : JbObjectMapper<JbString>() {
    internal var string: String? = null

    override fun parseHeader(mandatory: Boolean) {
        if (mandatory) {
            require(isString())
            val view = view()
            val start = offset()
            when (val raw = view.getInt8(start).toInt() and 0xf) {
                in 0..12 -> setContent(start + 1, start + 1 + raw)
                13 -> setContent(start + 2, start + 2 + view.getInt8(start + 1).toInt() and 0xff)
                14 -> setContent(start + 3, start + 3 + view.getInt16(start + 1).toInt() and 0xffff)
                15 -> setContent(start + 5, start + 5 + view.getInt32(start + 1))
                else -> throw IllegalStateException()
            }
        }
        string = null
    }

    /**
     * Returns the amount of byte encoded in the string.
     */
    fun length(): Int {
        return encodingEnd - encodingStart
    }

    /**
     * Read the code-point at the current offset.
     * @param moveForward If true, the offset will be adjusted after reading the code point; false otherwise.
     * @return The unicode of the code-point at the current offset.
     */
    fun readCodePoint(moveForward:Boolean) : Int {
        val view = view()
        if (offset >= encodingEnd) return -1
        var unicode = view.getInt8(offset).toInt() and 0xff
        if (unicode < 128) {
            if (moveForward) {
                offset += 1
            }
            return unicode
        }
        // Multibyte encoding
        if (unicode ushr 6 == 0b10) {
            // Two byte encoding
            check(offset + 1 < encodingEnd)
            unicode = ((unicode and 0b111111) shl 8) or view.getInt8(offset + 1).toInt() and 0xff
            if (moveForward) {
                offset += 2
            }
            return unicode
        }
        // Three byte encoding
        check(offset + 2 < encodingEnd)
        unicode = ((unicode and 0b111111) shl 16) or view.getInt16(offset + 1).toInt() and 0xffff
        if (moveForward) {
            offset += 3
        }
        return unicode
    }

    override fun toString(): String {
        if (string == null) {
            val sb = StringBuilder(length())
            val backup = offset
            offset = encodingStart
            while (offset < encodingEnd) {
                val unicode = readCodePoint(true)
                if (CodePoints.isBmpCodePoint(unicode)) {
                    sb.append(unicode.toChar())
                } else {
                    sb.append(CodePoints.highSurrogate(unicode))
                    sb.append(CodePoints.lowSurrogate(unicode))
                }
            }
            offset = backup
            string = sb.toString()
        }
        return string as String
    }
}