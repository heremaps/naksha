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
    /**
     * The view to which the reader maps.
     */
    internal var view: IDataView? = null

    /**
     * The current offset in the view, starts with zero.
     */
    internal var offset = 0

    /**
     * The local dictionary to be used when decoding text or references.
     */
    var localDict: JbDict? = null

    /**
     * The global dictionary to be used when decoding text or references.
     */
    var globalDict: JbDict? = null

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
     * @throws IllegalStateException If the view is invalid.
     */
    fun view(): IDataView {
        return view ?: throw IllegalStateException("view")
    }

    /**
     * Returns the current offset.
     * @return the current offset.
     */
    fun offset(): Int {
        return offset
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
     * Set the offset in the [view].
     * @param pos The offset to set, must be between 0 and view size.
     * @return this.
     * @throws IllegalStateException If the view is null.
     * @throws IllegalArgumentException If the given offset is out of bounds.
     */
    fun setOffset(pos: Int): JbReader {
        val view = view()
        require(pos in 0..view.getSize())
        this.offset = pos
        return this
    }

    /**
     * Adds the given amount to the current offset.
     * @param amount The amount of byte add to the [offset].
     * @return this.
     */
    fun addOffset(amount: Int): JbReader {
        setOffset(offset + amount)
        return this
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
     * Skip to the next entity.
     * @return true if there is more; false otherwise.
     */
    fun next(): Boolean {
        val view = this.view ?: return false
        val offset = this.offset + size()
        if (offset < 0 || offset > view.getSize()) return false
        this.offset = offset
        return offset < view.getSize()
    }

    /**
     * Skip the lead-in byte and move into an object. Works only for objects for which an [object-mapper](JbObjectMapper)
     * exist.
     *
     * @return true if moved; false otherwise.
     */
    internal fun enter(): Boolean {
        val type = type()
        return when (type) {
            TYPE_CONTAINER,
            TYPE_LOCAL_DICTIONARY,
            TYPE_GLOBAL_DICTIONARY,
            TYPE_STRING,
            TYPE_FEATURE -> {
                addOffset(1)
                true
            }

            else -> false
        }
    }

    /**
     * Reads the type from the current position.
     * @return The type stored at the current position.
     */
    fun type(): Int {
        val view = this.view ?: return EOF
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return EOF
        var type = view.getInt8(offset).toInt() and 0xff
        if (type and 128 == 128) {
            type = type and 0xf0
        }
        return type
    }

    /**
     * Returns the size of the current value in byte including the lead-in byte, therefore the amount of byte to [addOffset], if
     * wanting to skip the value to the next one. The method return 0, when the position is invalid.
     * @return The size of the value in bytes including the lead-in (so between 1 and n), 0 when EOF.
     */
    fun size(): Int {
        val view = view()
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
                    0 -> { // Empty container
                        1
                    }

                    1 -> view.getInt8(offset + 1).toInt() and 0xff
                    2 -> view.getInt16(offset + 1).toInt() and 0xffff
                    3 -> view.getInt32(offset + 1)
                    else -> throw IllegalStateException()
                }
            }

            TYPE_GLOBAL_DICTIONARY, TYPE_LOCAL_DICTIONARY, TYPE_FEATURE -> {
                this.offset++
                try {
                    return readInt32(0)
                } finally {
                    this.offset = offset
                }
            }

            else -> 0
        }
    }

    /**
     * Tests whether the type at the current position is a boolean.
     * @return true if the type at the current position is a boolean; false otherwise.
     */
    fun isBool(): Boolean {
        val type = type()
        return type == TYPE_BOOL_TRUE || type == TYPE_BOOL_FALSE
    }

    fun isNull(): Boolean {
        return type() == TYPE_NULL
    }

    fun isUndefined(): Boolean {
        return type() == TYPE_UNDEFINED
    }

    fun readBoolean(): Boolean? {
        val type = type()
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
        val type = type()
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
        val type = type()
        return type == TYPE_UINT4
                || type == TYPE_SINT4
                || type == TYPE_INT8
                || type == TYPE_INT16
                || type == TYPE_INT32
    }

    fun readInt32(alternative: Int = -1): Int {
        val view = view()
        val offset = this.offset
        val type = type()
        return when (type) {
            TYPE_UINT4 -> (view.getInt8(offset).toInt() and 0x0f)
            TYPE_SINT4 -> (view.getInt8(offset).toInt() and 0x0f) - 16
            TYPE_INT8 -> view.getInt8(offset + 1).toInt()
            TYPE_INT16 -> view.getInt16(offset + 1).toInt()
            TYPE_INT32 -> view.getInt32(offset + 1)
            else -> alternative
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    fun readInt64(alternative: Long = -1): Long {
        val view = view()
        val type = type()
        return when (type) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> readInt32().toLong()
            TYPE_INT64 -> {
                val hi = view.getInt32(offset + 1)
                val lo = view.getInt32(offset + 5)
                ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff)
            }

            else -> alternative
        }
    }

    fun isFloat32(): Boolean {
        val type = type()
        return type == TYPE_FLOAT32 || type == TYPE_FLOAT4
    }

    fun isFloat64(): Boolean {
        val type = type()
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
        val view = view()
        val offset = this.offset
        return when (type()) {
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
                readDouble().toFloat()
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
    fun readDouble(alternative: Double = Double.NaN, readStrict: Boolean = false): Double {
        val view = view()
        val offset = this.offset
        return when (type()) {
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
        return type() == TYPE_STRING
    }

    /**
     * Test if the current offset is at the lead-in of a text.
     * @return true if the current offset is at the lead-in of a text; false otherwise.
     */
    fun isText(): Boolean {
        val view = view()
        val offset = this.offset
        if (offset < 0 || offset >= view.getSize()) return false
        val raw = view.getInt8(offset).toInt() and 0xff
        var type = raw
        if (type and 128 == 128) {
            type = type and 0xf0
        }
        if (type == TYPE_CONTAINER) {
            val value = raw and 0xf
            return value and 0b1100 == 0b1100
        }
        return false
    }

    private var stringReader: JbString? = null

    private fun stringReader(): JbString {
        var reader = this.stringReader
        if (reader == null) {
            reader = JbString()
            stringReader = reader
        }
        reader.mapReader(this)
        return reader
    }

    /**
     * Uses an internal string reader to parse the string at the current [offset] and return it.
     * @param reader The reader to use, if null, an internal cached reader is used and returned.
     * @return The given reader mapped to this string or the internal cached reader mapped to this string.
     */
    fun readString(): String {
        val stringReader = stringReader()
        if (stringReader.view == view && stringReader.offset == offset && stringReader.string != null) {
            return stringReader.string!!
        }
        return stringReader.mapReader(this).toString()
    }

    // readText() : String

    fun isRef(): Boolean {
        val type = type()
        return type == TYPE_REFERENCE || type == TYPE_TINY_GLOBAL_REF || type == TYPE_TINY_LOCAL_REF
    }

    fun isLocalRef(): Boolean {
        val view = view()
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
        val view = view()
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
        val view = view()
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
        return type() == TYPE_LOCAL_DICTIONARY
    }

    /**
     * Test if the current offset is at the lead-in of a global dictionary.
     * @return true if the current type is a global dictionary; false otherwise.
     */
    fun isGlobalDict(): Boolean {
        return type() == TYPE_GLOBAL_DICTIONARY
    }

}