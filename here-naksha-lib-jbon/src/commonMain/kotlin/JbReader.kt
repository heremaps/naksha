@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A low level JBON reader that can be used with any data.
 */
@Suppress("DuplicatedCode")
@JsExport
open class JbReader {

    companion object {
        fun readMap(jbMap: JbMap): IMap {
            val imap = newMap()
            while (jbMap.next()) {
                imap.put(jbMap.key(), jbMap.value().readValue())
            }
            return imap
        }

        fun readArray(jbArray: JbArray): Array<Any?> {
            val arr = Array<Any?>(jbArray.length()) {}
            var i = 0
            while (jbArray.next() && jbArray.ok()) {
                arr[i] = jbArray.value().readValue()
                i += 1
            }
            return arr
        }

        /**
         * Calculate the amount of bytes needed to store the given integer value.
         * @param value The value to store.
         * @return The amount of byte that are needed to encode this integer.
         */
        fun sizeOfIntEncoding(value: Int): Int {
            return when (value) {
                in -16..15 -> 1
                in -128..127 -> 2
                in -32768..32767 -> 3
                else -> 5
            }
        }

        /**
         * Calculate the amount of bytes needed to store the given integer value.
         * @param value The value to store.
         * @return The amount of byte that are needed to encode this integer.
         */
        fun sizeOfBigIntEncoding(value: BigInt64): Int {
            if (value.gtei(Int.MAX_VALUE) || value.ltei(Int.MIN_VALUE)) return 9
            return sizeOfIntEncoding(value.toInt())
        }

    }

    /**
     * The view to which the reader maps, if any.
     */
    var view: IDataView? = null

    /**
     * The local dictionary to be used when decoding text or references.
     */
    var localDict: JbDict? = null

    /**
     * The global dictionary to be used when decoding text or references.
     */
    var globalDict: JbDict? = null

    /**
     * The current offset in the view, starts with zero.
     */
    var offset = 0

    /**
     * Maps the given view.
     * @param view The view to map.
     * @param start The start of the mapping.
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     * @return this.
     */
    open fun mapView(view: IDataView?, start: Int, localDict: JbDict? = null, globalDict: JbDict? = null): JbReader {
        this.view = view
        this.offset = start
        this.localDict = localDict
        this.globalDict = globalDict
        return this
    }

    /**
     * Returns the view.
     * @return The view.
     * @throws IllegalStateException If the view is _null_.
     */
    fun useView(): IDataView {
        return view ?: throw IllegalStateException("view")
    }

    /**
     * Set the offset in the [useView].
     * @param pos The offset to set, must be between 0 and view size.
     * @return this.
     * @throws IllegalStateException If the view is null.
     * @throws IllegalArgumentException If the given offset is out of bounds.
     */
    fun setOffset(pos: Int): JbReader {
        val view = useView()
        require(pos in 0..view.getSize())
        this.offset = pos
        return this
    }

    /**
     * Adds the given amount to the current offset.
     * @param amount The amount of byte add to the [getOffset].
     * @return this.
     */
    fun addOffset(amount: Int): JbReader {
        setOffset(offset + amount)
        return this
    }

    /**
     * Tests if the given offset is valid.
     * @param offset The offset to test for.
     * @return true if the offset can be read.
     */
    fun isValid(offset: Int): Boolean {
        val view = this.view ?: return false
        return offset >= 0 && offset < view.getSize()
    }

    /**
     * Tests whether the reader is end-of-file.
     * @return true if the reader is invalid (can't be read); false if ready for reading.
     */
    fun eof(): Boolean {
        val view = this.view ?: return false
        val offset = this.offset
        return offset < 0 || offset >= view.getSize()
    }

    /**
     * Tests whether the reader is valid, it can be read from.
     * @return true if the reader can be used for reading; false otherwise.
     */
    fun ok(): Boolean {
        return !eof()
    }

    /**
     * Skip to the next unit.
     * @return true if a new unit reached; false otherwise.
     */
    fun nextUnit(): Boolean {
        val view = this.view ?: return false
        val unitSize = unitSize()
        if (unitSize <= 0) return false
        val offset = this.offset + unitSize
        if (offset < 0 || offset > view.getSize()) return false
        this.offset = offset
        return offset < view.getSize()
    }

    /**
     * Reads the type from the current position.
     * @return The type stored at the current position.
     */
    open fun unitType(): Int {
        val view = this.view ?: return EOF
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return EOF
        val type = view.getInt8(offset).toInt() and 0xff
        if (type and 128 == 128) {
            // Primary parameter type: 1ttt_pppp
            // There is one special case, the container, where the top two bit of the
            // parameter belong to the base-type, and only the lower two bit are parameter.
            val baseType = type and 0xf0
            return if (baseType == TYPE_CONTAINER) type and 0b1111_1100 else baseType
        }
        return type
    }

    /**
     * Returns the 4-bit parameter of a parameter-type.
     * @param alternative The value to return, when this is no value type.
     * @return The parameter value or the alternative.
     */
    fun unitTypeParam(alternative: Int = -1): Int {
        val view = this.view ?: return alternative
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return alternative
        val type = view.getInt8(offset).toInt() and 0xff
        if (type and 128 == 128) {
            return type and 0x0f
        }
        return alternative
    }

    /**
     * Returns the size of the current value in byte including the lead-in byte, therefore the amount of byte to [addOffset], if
     * wanting to skip the unit to the next one. The method return 0, when the position is invalid.
     * @return The size of the value in bytes including the lead-in (so between 1 and n), 0 when EOF.
     */
    fun unitSize(): Int {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return 0
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        return when (type) {
            TYPE_NULL,
            TYPE_UNDEFINED,
            TYPE_BOOL_TRUE,
            TYPE_BOOL_FALSE,
            TYPE_UINT4,
            TYPE_SINT4,
            TYPE_TINY_LOCAL_REF,
            TYPE_TINY_GLOBAL_REF,
            TYPE_FLOAT4 -> {
                return 1
            }

            TYPE_INT8 -> 2
            TYPE_INT16 -> 3
            TYPE_INT32 -> 5
            TYPE_INT64 -> 9
            TYPE_FLOAT32 -> 5
            TYPE_FLOAT64 -> 9
            TYPE_TIMESTAMP -> 7

            TYPE_REFERENCE -> {
                when (raw and 3) {
                    0 -> 1
                    1 -> 2 // 8-bit index
                    2 -> 3 // 16-bit index
                    3 -> 5 // 32-bit index
                    else -> throw IllegalStateException()
                }
            }

            TYPE_STRING -> {
                when (val size = raw and 0xf) {
                    in 0..12 -> 1 + size
                    13 -> 2 + view.getInt8(offset + 1).toInt() and 0xff
                    14 -> 3 + view.getInt16(offset + 1).toInt() and 0xffff
                    15 -> 5 + view.getInt32(offset + 1)
                    else -> throw IllegalStateException()
                }
            }

            TYPE_CONTAINER -> {
                when (raw and 3) {
                    0 -> 1 // Empty container
                    1 -> 2 + view.getInt8(offset + 1).toInt() and 0xff
                    2 -> 3 + view.getInt16(offset + 1).toInt() and 0xffff
                    3 -> 5 + view.getInt32(offset + 1)
                    else -> throw IllegalStateException()
                }
            }

            TYPE_GLOBAL_DICTIONARY, TYPE_LOCAL_DICTIONARY, TYPE_FEATURE, TYPE_XYZ -> {
                this.offset++
                try {
                    // We seeked to the size, which is as well just a unit.
                    // The unit-size of all structures actually is:
                    // lead-in byte (1 byte) + the size + size of the size
                    check(isInt())
                    return 1 + readInt32(0) + unitSize()
                } finally {
                    this.offset = offset
                }
            }

            else -> 0
        }
    }

    /**
     * Move the [offset] into the unit, skipping the lead-in byte and the (optional) size field.
     * @return _True_ if the unit was entered successfully; _false_ if the current unit can't be entered.
     */
    fun enterUnit(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        when (type) {
            TYPE_NULL,
            TYPE_UNDEFINED,
            TYPE_BOOL_TRUE,
            TYPE_BOOL_FALSE,
            TYPE_UINT4,
            TYPE_SINT4,
            TYPE_TINY_LOCAL_REF,
            TYPE_TINY_GLOBAL_REF,
            TYPE_FLOAT4,
            TYPE_INT8,
            TYPE_INT16,
            TYPE_INT32,
            TYPE_INT64,
            TYPE_FLOAT32,
            TYPE_FLOAT64,
            TYPE_TIMESTAMP,
            TYPE_REFERENCE -> {
                return false
            }

            TYPE_STRING -> {
                when (raw and 0xf) {
                    in 0..12 -> this.offset += 1
                    13 -> this.offset += 2
                    14 -> this.offset += 3
                    15 -> this.offset += 5
                    else -> return false
                }
                return true
            }

            TYPE_CONTAINER -> {
                when (raw and 3) {
                    0 -> this.offset += 1 // Empty container
                    1 -> this.offset += 2
                    2 -> this.offset += 3
                    3 -> this.offset += 5
                    else -> return false
                }
                return true
            }

            TYPE_GLOBAL_DICTIONARY, TYPE_LOCAL_DICTIONARY, TYPE_FEATURE, TYPE_XYZ -> {
                this.offset++
                // We seeked to the size, which is as well just a unit.
                // The unit-size of all structures actually is:
                // lead-in byte (1 byte) + the size + size of the size
                check(isInt())
                this.offset += unitSize()
                return true
            }
        }
        return false
    }

    /**
     * Tests whether the type at the current position is a boolean.
     * @return true if the type at the current position is a boolean; false otherwise.
     */
    fun isBool(): Boolean {
        val type = unitType()
        return type == TYPE_BOOL_TRUE || type == TYPE_BOOL_FALSE
    }

    fun isNull(): Boolean {
        return unitType() == TYPE_NULL
    }

    fun isUndefined(): Boolean {
        return unitType() == TYPE_UNDEFINED
    }

    fun readBoolean(): Boolean? {
        val type = unitType()
        return when (type) {
            TYPE_BOOL_TRUE -> true
            TYPE_BOOL_FALSE -> false
            else -> null
        }
    }

    /**
     * Tests whether the value is any integer value.
     * @return true if the value is an integer; false otherwise.
     */
    fun isInt(): Boolean {
        val type = unitType()
        return type == TYPE_UINT4
                || type == TYPE_SINT4
                || type == TYPE_INT8
                || type == TYPE_INT16
                || type == TYPE_INT32
                || type == TYPE_INT64
    }

    /**
     * Tests whether the value is a 32-bit integer value, this excludes 64-bit integers.
     * @return true if the value is an integer; false otherwise.
     */
    fun isInt32(): Boolean {
        val type = unitType()
        return type == TYPE_UINT4
                || type == TYPE_SINT4
                || type == TYPE_INT8
                || type == TYPE_INT16
                || type == TYPE_INT32
    }

    fun readInt32(alternative: Int = -1): Int {
        val view = useView()
        val offset = this.offset
        val type = unitType()
        return when (type) {
            TYPE_UINT4 -> (view.getInt8(offset).toInt() and 0x0f)
            TYPE_SINT4 -> (view.getInt8(offset).toInt() and 0x0f) - 16
            TYPE_INT8 -> view.getInt8(offset + 1).toInt()
            TYPE_INT16 -> view.getInt16(offset + 1).toInt()
            TYPE_INT32 -> view.getInt32(offset + 1)
            else -> alternative
        }
    }

    fun readInt64(alternative: BigInt64? = null): BigInt64 {
        val view = useView()
        val type = unitType()
        return when (type) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> Jb.int64.intToBigInt64(readInt32())
            TYPE_INT64 -> view.getBigInt64(offset + 1)
            else -> alternative ?: Jb.int64.intToBigInt64(0)
        }
    }

    fun isTimestamp(): Boolean {
        return unitType() == TYPE_TIMESTAMP
    }

    fun readTimestamp(): BigInt64 {
        val view = useView()
        check(unitType() == TYPE_TIMESTAMP)
        val hi = (view.getInt16(offset + 1).toLong() and 0xffff) shl 32
        return BigInt64(hi or (view.getInt32(offset + 3).toLong() and 0xffff_ffff))
    }

    fun isFloat32(): Boolean {
        val type = unitType()
        return type == TYPE_FLOAT32 || type == TYPE_FLOAT4
    }

    fun isFloat64(): Boolean {
        val type = unitType()
        return type == TYPE_FLOAT64 || type == TYPE_FLOAT4
    }

    fun isNumber(): Boolean {
        return isInt() || isFloat32() || isFloat64()
    }

    /**
     * Read a float from the current position. Fails and returns the alternative, when the value is not convertible into a float.
     *
     * @param alternative The value to return, when the value can't be read as a float.
     * @param readStrict If set to true, the method does not lose precision, rather returns the alternative.
     */
    fun readFloat32(alternative: Float = Float.NaN, readStrict: Boolean = false): Float {
        val view = useView()
        val offset = this.offset
        return when (unitType()) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16 -> {
                readInt32().toFloat()
            }

            TYPE_INT32 -> {
                if (readStrict) return alternative
                readInt32().toFloat()
            }

            TYPE_INT64 -> {
                if (readStrict) return alternative
                readInt64().toFloat()
            }

            TYPE_FLOAT4 -> {
                val index = view.getInt8(offset).toInt() and 0xf
                TINY_FLOATS[index]
            }

            TYPE_FLOAT32 -> {
                view.getFloat32(offset + 1)
            }

            TYPE_FLOAT64 -> {
                if (readStrict) return alternative
                readFloat64().toFloat()
            }

            else -> alternative
        }
    }

    /**
     * Read a double from the current position. Fails and returns the alternative, when the value is not convertible into a double.
     *
     * @param alternative The value to return, when the value can't be read as a double.
     * @param readStrict If set to true, the method does not lose precision, rather returns the alternative.
     */
    fun readFloat64(alternative: Double = Double.NaN, readStrict: Boolean = false): Double {
        val view = useView()
        val offset = this.offset
        return when (unitType()) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> {
                readInt32().toDouble()
            }

            TYPE_INT64 -> {
                if (readStrict) return alternative
                readInt64().toDouble()
            }

            TYPE_FLOAT4 -> {
                val index = view.getInt8(offset).toInt() and 0xf
                TINY_DOUBLES[index]
            }

            TYPE_FLOAT32 -> {
                readFloat32().toDouble()
            }

            TYPE_FLOAT64 -> {
                view.getFloat64(offset + 1)
            }

            else -> alternative
        }
    }

    /**
     * Test if the current offset is at the lead-in of a string.
     * @return true if the current type is a string; false otherwise.
     */
    fun isString(): Boolean {
        return unitType() == TYPE_STRING
    }

    private var stringReader: JbString? = null

    private fun stringReader(): JbString {
        var sr = stringReader
        if (sr == null) {
            sr = JbString()
            stringReader = sr
        }
        return sr
    }

    /**
     * Uses an internal string reader to parse the string at the current [getOffset] and return it.
     * @return The parsed string.
     */
    fun readString(): String {
        val stringReader = stringReader()
        // If the exact same string is read multiple times, avoid multiple parses.
        if (stringReader.reader.view == view && stringReader.start == offset) {
            val string = stringReader.string
            if (string != null) return string
        }
        return stringReader.mapReader(this).toString()
    }

    private var textReader: JbText? = null

    private fun textReader(): JbText {
        var tr = textReader
        if (tr == null) {
            tr = JbText()
            textReader = tr
        }
        return tr
    }


    /**
     * Uses an internal text reader to parse the text at the current [getOffset] and return it.
     * @return The parsed text as string.
     */
    fun readText(): String {
        val textReader = textReader()
        // If the exact same string is read multiple times, avoid multiple parses.
        if (textReader.reader.view == view && textReader.start == offset) {
            val string = textReader.string
            if (string != null) return string
        }
        return textReader.mapReader(this).toString()
    }

    fun isRef(): Boolean {
        val type = unitType()
        return type == TYPE_REFERENCE || type == TYPE_TINY_GLOBAL_REF || type == TYPE_TINY_LOCAL_REF
    }

    fun isLocalRef(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        if (type == TYPE_REFERENCE) {
            return raw and 0b0000_0100 == 0
        }
        return type == TYPE_TINY_LOCAL_REF
    }

    fun isGlobalRef(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        if (type == TYPE_REFERENCE) {
            return raw and 0b0000_0100 != 0
        }
        return type == TYPE_TINY_GLOBAL_REF
    }

    fun readRef(): Int {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return -1
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        val value = raw and 0xf
        return when (type) {
            TYPE_TINY_LOCAL_REF, TYPE_TINY_GLOBAL_REF -> value
            TYPE_REFERENCE -> {
                return when (value and 3) {
                    0 -> -1
                    1 -> (view.getInt8(offset + 1).toInt() and 0xff) + 16
                    2 -> (view.getInt16(offset + 1).toInt() and 0xffff) + 16
                    3 -> view.getInt32(offset + 1) + 16
                    else -> throw IllegalStateException()
                }
            }

            else -> -1
        }
    }

    /**
     * Test if the current offset is at the lead-in of a local dictionary.
     * @return true if the current type is a local dictionary; false otherwise.
     */
    fun isLocalDict(): Boolean {
        return unitType() == TYPE_LOCAL_DICTIONARY
    }

    /**
     * Test if the current offset is at the lead-in of a global dictionary.
     * @return true if the current type is a global dictionary; false otherwise.
     */
    fun isGlobalDict(): Boolean {
        return unitType() == TYPE_GLOBAL_DICTIONARY
    }

    fun isMap(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        return (type == TYPE_CONTAINER) && (raw and 0b0000_1100 == TYPE_CONTAINER_MAP)
    }


    fun isArray(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        return (type == TYPE_CONTAINER) && (raw and 0b0000_1100 == TYPE_CONTAINER_ARRAY)
    }

    /**
     * Test if the current offset is at the lead-in of a text.
     * @return true if the current offset is at the lead-in of a text; false otherwise.
     */
    fun isText(): Boolean {
        val view = useView()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        return (type == TYPE_CONTAINER) && (raw and 0b0000_1100 == TYPE_CONTAINER_TEXT)
    }

    /**
     * Test if the current offset is at the lead-in of an XYZ special.
     * @return true if the current offset is at the lead-in of an XYZ special; false otherwise.
     */
    fun isXyz(): Boolean = unitType() == TYPE_XYZ

    /**
     * Returns the XYZ variant or _-1_, if not being an XYZ type.
     * @return The XYZ variant or _-1_, if not being an XYZ type.
     */
    fun xyzVariant(): Int = if (unitType() == TYPE_XYZ) useView().getInt8(offset + 1).toInt() else -1

    /**
     * Read the current unit as _null_, [Boolean], [Int], [BigInt64], [Double], [String], [IMap] or [Array].
     * @return the current unit as _null_, [Boolean], [Int], [BigInt64], [Double], [String], [IMap] or [Array].
     * @throws IllegalStateException If the reader position or the unit-type is invalid.
     */
    fun readValue(): Any? {
        return if (isInt32()) {
            readInt32()
        } else if (isInt()) {
            readInt64()
        } else if (isString()) {
            readString()
        } else if (isBool()) {
            readBoolean()
        } else if (isFloat64() || isFloat32()) {
            readFloat64()
        } else if (isNull()) {
            null
        } else if (isText()) {
            readText()
        } else if (isTimestamp()) {
            readTimestamp()
        } else if (isMap()) {
            readMap(JbMap().mapReader(this))
        } else if (isArray()) {
            readArray(JbArray().mapReader(this))
        } else {
            throw IllegalStateException("Not implemented jbon value type")
        }
    }
}