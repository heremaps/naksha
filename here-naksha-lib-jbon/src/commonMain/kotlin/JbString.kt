@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to read strings.
 */
@JsExport
class JbString() {
    internal var string: String? = null
    internal var view: IDataView? = null
    internal var start: Int = 0
    private var encodingStart: Int = 0
    private var encodingEnd: Int = 0
    internal var pos: Int = 0

    /**
     * Map a specific region of a view as string. If the start and content-state are equal, the only effect will be that
     * the wrong size is reported, decoding will still work fine.
     *
     * @param view The view to map.
     * @param leadInOffset The offset where the string starts (header).
     * @param contentOffset The offset where the string encoding starts (behind the header).
     * @param end The end of the string (the offset of the first byte that does not belong to the string).
     * @return this
     */
    fun mapRaw(view : IDataView, leadInOffset : Int, contentOffset: Int, end : Int) : JbString {
        require(leadInOffset in 0..contentOffset && contentOffset <= end && end <= view.getSize())
        this.view = view
        start = leadInOffset
        encodingStart = contentOffset
        encodingEnd = end
        pos = encodingStart
        string = null
        return this
    }

    fun mapReader(jbon: JbReader): JbString {
        require(jbon.isString())
        val view = jbon.view
        start = jbon.pos
        when (val raw = view.getInt8(start).toInt() and 0xf) {
            in 0..12 -> {
                encodingStart = start + 1
                encodingEnd = encodingStart + raw
            }

            13 -> {
                encodingStart = start + 2
                encodingEnd = encodingStart + view.getInt8(start + 1).toInt() and 0xff
            }

            14 -> {
                encodingStart = start + 3
                encodingEnd = encodingStart + view.getInt16(start + 1).toInt() and 0xffff
            }

            15 -> {
                encodingStart = start + 5
                encodingEnd = encodingStart + view.getInt32(start + 1)
            }

            else -> throw IllegalStateException()
        }
        this.view = view
        pos = encodingStart
        string = null
        return this
    }

    /**
     * Returns the amount of byte encoded in the string.
     */
    fun length(): Int {
        return encodingEnd - encodingStart
    }

    /**
     * Returns the total size in byte including the lead-in.
     */
    fun size(): Int {
        return encodingEnd - start
    }

    fun reset(): JbString {
        pos = encodingStart
        return this
    }

    fun next(doNotMoveForward: Boolean = false): Int {
        check(this.view != null)
        val view = this.view!!
        if (pos >= encodingEnd) return -1
        var unicode = view.getInt8(pos).toInt() and 0xff
        if (unicode < 128) {
            if (!doNotMoveForward) {
                pos += 1
            }
            return unicode
        }
        // Multibyte encoding
        if (unicode ushr 6 == 0b10) {
            // Two byte encoding
            check(pos + 1 < encodingEnd)
            unicode = ((unicode and 0b111111) shl 8) or view.getInt8(pos + 1).toInt() and 0xff
            if (!doNotMoveForward) {
                pos += 2
            }
            return unicode
        }
        // Three byte encoding
        check(pos + 2 < encodingEnd)
        unicode = ((unicode and 0b111111) shl 16) or view.getInt16(pos + 1).toInt() and 0xffff
        if (!doNotMoveForward) {
            pos += 3
        }
        return unicode
    }

    override fun toString(): String {
        if (string == null) {
            val sb = StringBuilder(length())
            val backup = pos
            pos = encodingStart
            while (pos < encodingEnd) {
                val unicode = next()
                if (CodePoints.isBmpCodePoint(unicode)) {
                    sb.append(unicode.toChar())
                } else {
                    sb.append(CodePoints.highSurrogate(unicode))
                    sb.append(CodePoints.lowSurrogate(unicode))
                }
            }
            pos = backup
            string = sb.toString()
        }
        return string as String
    }
}