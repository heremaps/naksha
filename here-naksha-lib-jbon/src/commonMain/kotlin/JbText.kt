@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A mapper for text, which are strings that contain references to other strings.
 */
@JsExport
class JbText : JbUnicodeMapper<JbText>() {
    /**
     * The cached text.
     */
    internal var string: String? = null

    override fun parseHeader(mandatory: Boolean) {
        if (mandatory) {
            val view = reader.view()
            check(reader.unitType() == TYPE_CONTAINER)
            val param = reader.unitTypeParam()
            check(param and 0b0000_1100 == TYPE_CONTAINER_TEXT)
            val sizeIndicator = param and 0b0000_0011
            val size: Int
            when (sizeIndicator) {
                1 -> {
                    size = 1 + view.getInt8(reader.offset + 1).toInt() and 0xff
                    reader.offset += 2
                }

                2 -> {
                    size = 3 + view.getInt16(reader.offset + 1).toInt() and 0xffff
                    reader.offset += 3
                }

                3 -> {
                    size = 5 + view.getInt32(reader.offset + 1)
                    reader.offset += 5
                }

                else -> {
                    size = 0
                }
            }
            setContentSize(size)
        }
        string = null
    }

    override fun toString() : String {
        var s = string
        if (s == null) {
            val sb = StringBuilder()
            val reader = reader
            val view = reader.view()
            val backup = reader.offset
            reader.offset = encodingStart
            while (reader.offset < encodingEnd) {
                val unitType = view.getInt8(reader.offset()).toInt()
                // 111_ssgvv = reference
                if (unitType and 0b1110_0000 == 0b1110_0000) {
                    // vv-bits
                    val sizeIndicator = unitType and 0b0000_0011
                    val index : Int
                    when (sizeIndicator) {
                        1 -> {
                            index = view.getInt8(reader.offset + 1).toInt() and 0xff
                            reader.offset += 2
                        }
                        2 -> {
                            index = view.getInt16(reader.offset + 1).toInt() and 0xffff
                            reader.offset += 3
                        }
                        3 -> {
                            index = view.getInt32(reader.offset + 1)
                            reader.offset += 5
                        }
                        else -> {
                            // null-reference, shouldn't be here, but simply ignore it.
                            reader.offset++
                            continue
                        }
                    }
                    // g-bit
                    val isGlobalRef = (unitType and 0b0000_0100) == 0b0000_0100
                    val dict = if (isGlobalRef) reader.globalDict else reader.localDict
                    check(dict != null)
                    val dictString = dict.get(index)
                    sb.append(dictString)

                    // When we should add some character (ss-bits).
                    val add = (unitType ushr 3) and 3
                    when (add) {
                        ADD_SPACE -> sb.append(' ')
                        ADD_UNDERSCORE -> sb.append('_')
                        ADD_COLON -> sb.append(':')
                    }
                } else {
                    // 0vvv_vvvv, 10_vvvvvv, 110_vvvvv = 1,2 or 3 byte unicode
                    val unicode = readCodePoint(true)
                    if (CodePoints.isBmpCodePoint(unicode)) {
                        sb.append(unicode.toChar())
                    } else {
                        sb.append(CodePoints.highSurrogate(unicode))
                        sb.append(CodePoints.lowSurrogate(unicode))
                    }
                }
            }
            reader.offset = backup
            s = sb.toString()
            string = s
        }
        return s
    }
}