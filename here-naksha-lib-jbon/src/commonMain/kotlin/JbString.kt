@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A string reader.
 */
@Suppress("DuplicatedCode")
@JsExport
class JbString : JbUnicodeMapper<JbString>() {
    internal var string: String? = null

    override fun parseHeader(mandatory: Boolean) {
        if (mandatory) {
            val view = reader.useView()
            val start = reader.offset
            val type = reader.unitType()
            check(type == TYPE_STRING);
            when (val raw = view.getInt8(start).toInt() and 0xf) {
                in 0..12 -> setContent(start + 1, start + 1 + raw)
                13 -> setContent(start + 2, start + 2 + (view.getInt8(start + 1).toInt() and 0xff))
                14 -> setContent(start + 3, start + 3 + (view.getInt16(start + 1).toInt() and 0xffff))
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

    override fun toString(): String {
        if (string == null) {
            val sb = StringBuilder(length())
            val backup = reader.offset
            reader.offset = encodingStart
            while (reader.offset < encodingEnd) {
                val unicode = readCodePoint(true)
                if (CodePoints.isBmpCodePoint(unicode)) {
                    sb.append(unicode.toChar())
                } else {
                    sb.append(CodePoints.highSurrogate(unicode))
                    sb.append(CodePoints.lowSurrogate(unicode))
                }
            }
            reader.offset = backup
            string = sb.toString()
        }
        return string as String
    }
}