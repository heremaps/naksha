@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import com.here.naksha.lib.base.BaseMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A low level JBON reader that can be used with any data. The reader
 */
@Suppress("DuplicatedCode", "PropertyName")
@JsExport
open class JbReader {

    companion object {
        /**
         * Returns the human-readable name for the given unit-type.
         * @param unitType The unit-type as returned by [unitType].
         * @return The human-readable name of this unit-type.
         */
        @JvmStatic
        fun unitTypeName(unitType: Int): String = when (unitType) {
            TYPE_NULL -> "null"
            TYPE_BOOL -> "boolean"
            TYPE_INT -> "int"
            TYPE_FLOAT -> "float"
            TYPE_REF -> "reference"
            TYPE_TIMESTAMP -> "timestamp"
            TYPE_STRING -> "string"
            TYPE_ARRAY -> "struct-array"
            TYPE_MAP -> "struct-map"
            TYPE_DICTIONARY -> "struct-dictionary"
            TYPE_FEATURE -> "struct-feature"
            TYPE_XYZ -> "struct-xyz"
            TYPE_CUSTOM -> "struct-custom"
            else -> "undefined"
        }

        /**
         * Returns the size of the unit-header based upon the given lead-in byte.
         * @param leadIn The lead-in byte, cast to integer.
         * @return The size of the header in byte (1..9).
         */
        @JvmStatic
        fun unitHeaderSize(leadIn: Int): Int = when (leadIn and ENC_MASK) {
            ENC_STRING -> when (leadIn and 0b0011_1111) {
                61 -> 2 // lead-in + 1 byte size
                62 -> 3 // lead-in + 2 byte size
                63 -> 5 // lead-in + 4 byte size
                // 0..60
                else -> 1 // lead-in only
            }

            ENC_STRUCT -> {
                var size = 1 // lead-in byte
                size += when (leadIn and ENC_STRUCT_SIZE_MASK) {
                    ENC_STRUCT_SIZE8 -> 1 // 1 byte size
                    ENC_STRUCT_SIZE16 -> 2 // 2 byte size
                    ENC_STRUCT_SIZE32 -> 4 // 4 byte size
                    //ENC_STRUCT_SIZE0
                    else -> 0 // no size (empty struct)
                }
                size += when (leadIn and ENC_STRUCT_VARIANT_MASK) {
                    ENC_STRUCT_VARIANT8 -> 1 // 1 byte variant
                    ENC_STRUCT_VARIANT16 -> 2 // 2 byte variant
                    ENC_STRUCT_VARIANT32 -> 4 // 4 byte variant
                    // ENC_STRUCT_VARIANT0
                    else -> 0 // no variant
                }
                size
            }

            // All scalars have only the lead-in byte as header.
            else -> 1
        }

        /**
         * Read the code-point at the current offset.
         * @param view The view.
         * @param offset The offset to start reading.
         * @param end The end-offset.
         * @return The read code point shift left by 3, the lower 3 bit store the amount of byte that have been read or -1, if eof or invalid encoding.
         */
        @JvmStatic
        internal fun readCodePoint(view: IDataView, offset: Int, end: Int): Int {
            if (offset >= end) return -1
            var unicode = view.getInt8(offset).toInt() and 0xff
            // One byte encoding.
            if (unicode < 128) return (unicode shl 3) or 1
            // Two byte encoding.
            if (unicode ushr 6 == 0b10) {
                if (offset + 1 >= end) return -1
                unicode = ((unicode and 0b111111) shl 8) or view.getInt8(offset + 1).toInt() and 0xff
                return (unicode shl 3) or 2
            }
            // Three byte encoding.
            if (offset + 2 >= end) return -1
            unicode = ((unicode and 0b111111) shl 16) or view.getInt16(offset + 1).toInt() and 0xffff
            return (unicode shl 3) or 3
        }

        /**
         * Read a part of a string starting at the given [offset] up until the given [end]. Requires exact matching
         * [offset] and [end].
         * @param view The view to read from.
         * @param offset The offset of the first byte to read.
         * @param end The offset of the first byte not to read, if less or equal [offset] nothing is read.
         * @param sb The string builder into which to encode the read characters (using UTF-16 encoding).
         * @param globalDict The global dictionary to use to decode string-references.
         * @param localDict The local dictionary to use to decode string-references.
         */
        @JvmStatic
        internal fun readSubstring(view: IDataView, offset: Int, end: Int, sb: StringBuilder, globalDict: JbDict? = null, localDict: JbDict? = null) {
            var i = offset
            while (i < end) {
                val codePointLeadIn = view.getInt8(i).toInt()
                // 111_ssgvv = reference
                if (codePointLeadIn and 0b1110_0000 == 0b1110_0000) {
                    // vv-bits
                    val sizeIndicator = codePointLeadIn and 0b0000_0011
                    val index: Int
                    when (sizeIndicator) {
                        1 -> {
                            index = view.getInt8(i + 1).toInt() and 0xff
                            i += 2
                        }

                        2 -> {
                            index = view.getInt16(i + 1).toInt() and 0xffff
                            i += 3
                        }

                        3 -> {
                            index = view.getInt32(i + 1)
                            i += 5
                        }

                        else -> {
                            // null-reference, shouldn't be here, but simply ignore it.
                            i++
                            continue
                        }
                    }
                    // g-bit
                    val isGlobalRef = (codePointLeadIn and 0b0000_0100) == 0b0000_0100
                    val dict = if (isGlobalRef) globalDict else localDict
                    check(dict != null) { "Decoding text references requires a dictionary, but reader does not have one" }
                    val dictString = dict.get(index)
                    sb.append(dictString)

                    // When we should add some character (ss-bits).
                    val add = (codePointLeadIn ushr 3) and 3
                    when (add) {
                        // 0 = ADD_NOTHING
                        ADD_SPACE -> sb.append(' ')
                        ADD_UNDERSCORE -> sb.append('_')
                        ADD_COLON -> sb.append(':')
                    }
                } else { // 0vvv_vvvv, 10_vvvvvv, 110_vvvvv = 1,2 or 3 byte unicode
                    var unicode = readCodePoint(view, i, end)
                    check(unicode != -1) { "Invalid unicode at offset $i (pos ${i - offset})" }
                    i += unicode and 0b111
                    unicode = unicode shr 3
                    if (CodePoints.isBmpCodePoint(unicode)) {
                        sb.append(unicode.toChar())
                    } else {
                        sb.append(CodePoints.highSurrogate(unicode))
                        sb.append(CodePoints.lowSurrogate(unicode))
                    }
                }
            }
        }

        /**
         * Convert the given JBON map into a platform native map.
         * @param jbMap The JBON map.
         * @return The platform native map.
         */
        @JvmStatic
        internal fun readMap(jbMap: JbMap): BaseMap<Any?> {
            val map = BaseMap.klass.newInstance()
            while (jbMap.next()) {
                map[jbMap.key()] = jbMap.value().readValue()
            }
            return map
        }

        /**
         * Convert the given JBON array into a platform native array.
         * @param jbArray The JBON array.
         * @return The platform native array.
         */
        @JvmStatic
        internal fun readArray(jbArray: JbArray): Array<Any?> {
            val arr = Array<Any?>(jbArray.length()) {}
            var i = 0
            while (jbArray.next() && jbArray.ok()) {
                arr[i] = jbArray.value().readValue()
                i += 1
            }
            return arr
        }
    }

    /**
     * The view to which the reader maps, if any.
     */
    private var _view: IDataView? = null

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
    private var _offset = 0

    /**
     * The current end of the view.
     */
    private var _end = 0

    /**
     * Clears the mapping of the reader.
     */
    open fun clear() {
        _view = null
        localDict = null
        globalDict = null
        reset()
        _end = 0
    }

    /**
     * Move the [offset] back to the start of the reader.
     */
    open fun reset() {
        _offset = 0
        _unitTypeOffset = -1
        _headerSizeOffset = -1
        _unitPayloadSizeOffset = -1
        _stringOffset = -1
    }

    /**
     * Maps the given view.
     * @param view The view to map.
     * @param offset The offset to start with, defaults to _0_.
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     * @return this.
     */
    open fun mapView(view: IDataView?, offset: Int = 0, localDict: JbDict? = null, globalDict: JbDict? = null): JbReader {
        clear()
        this._view = view
        this._offset = offset
        this._end = view?.getSize() ?: 0
        this.localDict = localDict
        this.globalDict = globalDict
        return this
    }

    /**
     * Maps the given foreign reader into this reader. Will share the global and local dictionaries, but not caches or offset.
     * @param reader The reader to map.
     */
    open fun mapReader(reader: JbReader) {
        clear()
        this._view = reader._view
        this._offset = reader._offset
        this._end = _view?.getSize() ?: 0
        this.localDict = reader.localDict
        this.globalDict = reader.globalDict
    }

    /**
     * Creates a view above the given bytes and maps this view for reading.
     * @param bytes The byte-array to map.
     * @param start The first byte to map.
     * @param end The end, being the first byte to exclude from mapping.
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     */
    open fun mapBytes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size, localDict: JbDict? = null, globalDict: JbDict? = null) {
        clear()
        val view = Jb.env.newDataView(bytes, start, end)
        this._view = view
        this._offset = 0
        this._end = view.getSize()
        this.localDict = localDict
        this.globalDict = globalDict
    }

    /**
     * Returns the view, checked to not being _null_.
     * @return The view.
     * @throws IllegalStateException If the view is _null_.
     */
    fun view(): IDataView {
        val view = this._view
        check(view != null) { "view must not be null" }
        return view
    }

    /**
     * Tests if the reader has a valid view.
     * @return _true_ if the reader has a valid view; _false_ otherwise (view is _null_).
     */
    fun isMapped(): Boolean = _view != null

    /**
     * Returns the current offset.
     * @return The current offset.
     */
    fun offset(): Int = _offset

    /**
     * Returns the current offset.
     * @return The current offset.
     */
    fun getOffset(): Int = _offset

    /**
     * Set the offset in the [view].
     * @param pos The offset to set, must be between 0 and view size.
     * @return this.
     * @throws IllegalStateException If the view is _null_.
     * @throws IllegalArgumentException If the given offset is out of bounds.
     */
    fun setOffset(pos: Int): JbReader {
        val view = view()
        require(pos in 0.._end) { "The offset must be between 0 and $_end (inclusive), but $pos was given" }
        this._offset = pos
        return this
    }

    /**
     * Adds the given amount to the current offset. If moved behind the end or in-front of the start, positioned at start or end.
     * @param amount The amount of byte add to the [getOffset].
     * @return this.
     */
    fun addOffset(amount: Int): JbReader {
        val view = _view
        if (view == null) {
            _offset = 0
        } else {
            var newOffset = _offset + amount
            if (newOffset > _end) newOffset = _end
            if (newOffset < 0) newOffset = 0
            _offset = newOffset
        }
        return this
    }

    /**
     * Returns the current end of the reader, so the first byte not to read from.
     * @return The end of the reader.
     */
    fun end(): Int = _end

    /**
     * Sets the end.
     * @param end The end to set.
     * @return this.
     */
    fun setEnd(end: Int): JbReader {
        require(end >= 0) { "Invalid end, must be greater/equal zero, but was: $end" }
        this._end = end
        return this
    }

    /**
     * Tests if the given offset is valid.
     * @param offset The offset to test for.
     * @return true if the offset can be read.
     */
    fun isValid(offset: Int): Boolean {
        val view = this._view ?: return false
        return offset >= 0 && offset < _end
    }

    /**
     * Tests whether the reader is end-of-file (at a not readable position).
     * @return _true_ if the reader is invalid (can't be read from); _false_ if ready for reading.
     */
    fun eof(): Boolean {
        val view = this._view ?: return true
        val offset = this._offset
        return offset < 0 || offset >= _end
    }

    /**
     * Tests whether the reader is valid, it can be read from.
     * @return _true_ if the reader can be used for reading; _false_ otherwise.
     */
    fun ok(): Boolean = !eof()

    /**
     * Skip to the next unit.
     * @return true if the next unit is [ok]; false if [eof].
     */
    fun nextUnit(): Boolean {
        val view = this._view ?: return false
        addOffset(unitSize())
        return _offset < _end
    }

    /**
     * Reads the lead-in byte from the current [_offset]. If [eof], the methods returns the lead-in byte for [TYPE_UNDEFINED].
     * @return The lead-in from the current [_offset].
     */
    open fun leadIn(): Int {
        val view = this._view ?: return ENC_MIXED_CONST_UNDEFINED
        val offset = this._offset
        if (offset < 0 || offset >= _end) return ENC_MIXED_CONST_UNDEFINED
        return view.getInt8(offset).toInt() and 0xff
    }

    private var _unitType = 0
    private var _unitTypeOffset = -1

    /**
     * Reads the type from the current position. Possible values are:
     * - [TYPE_UNDEFINED] _(scalar)_ - Returns when [eof], invalid lead-in byte or _undefined_ value.
     * - [TYPE_NULL] _(scalar)_
     * - [TYPE_BOOL] _(scalar)_
     * - [TYPE_INT] _(scalar)_ - All kind of integers (8-bit to 64-bit)
     * - [TYPE_FLOAT] _(scalar)_ All kind of floating point numbers (16-bit to 128-bit)
     * - [TYPE_REF] _(scalar)_ - A reference to global and local dictionaries.
     * - [TYPE_TIMESTAMP] _(scalar)_ - A 64-bit timestamp storing the UTC epoch milliseconds.
     * - [TYPE_STRING] _(string)_
     * - [TYPE_ARRAY] _(struct without variant)_
     * - [TYPE_MAP] _(struct without variant)_
     * - [TYPE_DICTIONARY] _(struct without variant)_
     * - [TYPE_FEATURE] _(struct)_
     * - [TYPE_XYZ] _(struct)_ - The XYZ type has as next unit a variant, use [unitVariant].
     * - [TYPE_CUSTOM] _(struct)_ - A custom structure.
     * This method caches the read result and therefore can be invoked safely multiple times.
     * @return The type stored at the current position.
     */
    open fun unitType(): Int {
        if (_offset != _unitTypeOffset) {
            val leadIn = leadIn()
            _unitTypeOffset = _offset
            var bits = leadIn and ENC_MASK
            when (bits) {
                ENC_STRING -> _unitType = TYPE_STRING
                ENC_STRUCT -> _unitType = CLASS_STRUCT or (leadIn and 0xf)
                ENC_TINY -> _unitType = if ((leadIn and ENC_TINY_MASK) == ENC_TINY_INT) TYPE_INT else TYPE_FLOAT
                else -> { // ENC_MIXED
                    bits = leadIn and ENC_MIXED_MASK
                    _unitType = if (bits != ENC_MIXED_CORS) TYPE_REF else when (leadIn) {
                        ENC_MIXED_SCALAR_INT8, ENC_MIXED_SCALAR_INT16, ENC_MIXED_SCALAR_INT32, ENC_MIXED_SCALAR_INT64 -> TYPE_INT
                        ENC_MIXED_SCALAR_FLOAT16, ENC_MIXED_SCALAR_FLOAT32, ENC_MIXED_SCALAR_FLOAT64, ENC_MIXED_SCALAR_FLOAT128 -> TYPE_FLOAT
                        ENC_MIXED_CONST_NULL -> TYPE_NULL
                        ENC_MIXED_CONST_TRUE, ENC_MIXED_CONST_FALSE -> TYPE_BOOL
                        ENC_MIXED_SCALAR_TIMESTAMP -> TYPE_TIMESTAMP
                        else -> TYPE_UNDEFINED
                    }
                }
            }
        }
        return _unitType
    }

    /**
     * Tests if the current unit is a scalar.
     * @return _true_ if the current unit is a scalar; _false_ otherwise.
     */
    fun isScalar(): Boolean = (unitType() and CLASS_SCALAR) == CLASS_SCALAR

    /**
     * Tests if the current unit is a string.
     * @return _true_ if the current unit is a string; _false_ otherwise.
     */
    fun isString(): Boolean = (unitType() and CLASS_STRING) == CLASS_STRING

    /**
     * Tests if the current unit is a string.
     * @return _true_ if the current unit is a string; _false_ otherwise.
     */
    fun isStruct(): Boolean = (leadIn() and ENC_MASK) == ENC_STRUCT

    private var _headerSize = 0
    private var _headerSizeOffset = -1

    /**
     * Returns the header size of the unit. This covers the lead-in byte, plus an optional size field, plus the optional variant.
     * @return The header size of the unit.
     */
    fun unitHeaderSize(): Int {
        if (_offset != _headerSizeOffset) {
            val leadIn = leadIn()
            _headerSizeOffset = _offset
            _headerSize = Companion.unitHeaderSize(leadIn)
        }
        return _headerSize
    }

    private var _unitPayloadSize = 0
    private var _unitPayloadSizeOffset = -1

    /**
     * Returns the size of the payload of the unit in byte. Can be zero, if the value is embedded into the lead-in byte,
     * for example for tiny integers, boolean, ...
     * @return The size of the payload of the unit in byte.
     */
    fun unitPayloadSize(): Int {
        if (_offset != _unitPayloadSizeOffset) {
            val leadIn = leadIn()
            val view = view()
            val offset = _offset
            _unitPayloadSizeOffset = offset
            _unitPayloadSize = when (leadIn and ENC_MASK) {
                ENC_TINY -> 0
                ENC_STRING -> when (val len = leadIn and 0b0011_1111) {
                    61 -> (view.getInt8(offset + 1).toInt() and 0xff) + 61
                    62 -> view.getInt16(offset + 1).toInt() and 0xffff
                    63 -> view.getInt32(offset + 1)
                    // 0 .. 60
                    else -> len
                }

                ENC_STRUCT -> when (leadIn and ENC_STRUCT_SIZE_MASK) {
                    ENC_STRUCT_SIZE8 -> view.getInt8(offset + 1).toInt() and 0xff
                    ENC_STRUCT_SIZE16 -> view.getInt16(offset + 1).toInt() and 0xffff
                    ENC_STRUCT_SIZE32 -> view.getInt32(offset + 1)
                    // ENC_STRUCT_SIZE0
                    else -> 0
                }

                else -> when (leadIn and ENC_MIXED_MASK) { // ENC_MIXED
                    ENC_MIXED_REF5_GLOBAL, ENC_MIXED_REF5_LOCAL -> 0
                    ENC_MIXED_CORS -> when (leadIn) {
                        ENC_MIXED_SCALAR_INT8 -> 1
                        ENC_MIXED_SCALAR_INT16 -> 2
                        ENC_MIXED_SCALAR_INT32 -> 4
                        ENC_MIXED_SCALAR_INT64 -> 8
                        ENC_MIXED_SCALAR_FLOAT16 -> 2
                        ENC_MIXED_SCALAR_FLOAT32 -> 4
                        ENC_MIXED_SCALAR_FLOAT64 -> 8
                        ENC_MIXED_SCALAR_FLOAT128 -> 16
                        ENC_MIXED_SCALAR_TIMESTAMP -> 6
                        else -> 0 // All other scalars do not have a payload (NULL, TRUE, FALSE, UNDEFINED, RESERVED)
                    }
                    // ENC_MIXED_REF
                    else -> when (leadIn and ENC_MIXED_REF_TYPE_MASK) {
                        ENC_MIXED_REF_INT8 -> 1
                        ENC_MIXED_REF_INT16 -> 2
                        ENC_MIXED_REF_INT32 -> 4
                        // ENC_MIXED_REF_NULL
                        else -> 0
                    }
                }
            }
        }
        return _unitPayloadSize
    }

    /**
     * Returns the size of the current unit in byte including the unit header, therefore the amount of byte to [addOffset], if
     * wanting to skip the unit to the next one. The method return 0, when [eof].
     * @return The size of the value in bytes including the unit-header (so between 1 and n), 0 when EOF.
     */
    fun unitSize(): Int = unitHeaderSize() + unitPayloadSize()

    /**
     * Returns the variant of the unit, if the unit has a variant (only variant structures do have).
     * @return The variant of the unit or _null_, if this unit does not have a variant.
     */
    fun unitVariant(): Int? {
        val leadIn = leadIn()
        if (leadIn and ENC_MASK == ENC_STRUCT) {
            val skipSize = when (leadIn and ENC_STRUCT_SIZE_MASK) {
                ENC_STRUCT_SIZE0 -> 1 + 0
                ENC_STRUCT_SIZE8 -> 1 + 1
                ENC_STRUCT_SIZE16 -> 1 + 2
                // ENC_STRUCT_SIZE32
                else -> 1 + 4
            }
            return when (leadIn and ENC_STRUCT_VARIANT_MASK) {
                ENC_STRUCT_VARIANT8 -> view().getInt8(_offset + skipSize).toInt() and 0xff
                ENC_STRUCT_VARIANT16 -> view().getInt16(_offset + skipSize).toInt() and 0xffff
                ENC_STRUCT_VARIANT32 -> view().getInt32(_offset + skipSize)
                // ENC_STRUCT_VARIANT0
                else -> null
            }
        }
        return null
    }

    /**
     * Move the [offset] into the unit, skipping the lead-in byte and the (optional) size field.
     * @return _True_ if the unit was entered successfully; _false_ if the current unit can't be entered.
     */
    fun enterStruct(): Boolean {
        val leadIn = leadIn()
        val baseType = leadIn and ENC_MASK
        if (baseType == ENC_STRUCT) {
            addOffset(unitHeaderSize())
            return true
        }
        return false
    }

    /**
     * Tests whether the type at the current position is a boolean.
     * @return true if the type at the current position is a boolean; false otherwise.
     */
    fun isBool(): Boolean = unitType() == TYPE_BOOL

    /**
     * Tests whether the current unity is _null_.
     * @return _true_ if the current unit is _null_; _false_ otherwise.
     */
    fun isNull(): Boolean = unitType() == TYPE_NULL

    /**
     * Tests whether the current unity is _undefined_.
     * @return _true_ if the current unit is _undefined_; _false_ otherwise.
     */
    fun isUndefined(): Boolean = unitType() == TYPE_UNDEFINED

    /**
     * Read the current unit as boolean.
     * @return The current unit as boolean; _null_ if the current unit is no boolean.
     */
    fun readBoolean(): Boolean? = when (leadIn()) {
        ENC_MIXED_CONST_TRUE -> true
        ENC_MIXED_CONST_FALSE -> false
        else -> null
    }

    /**
     * Tests whether the value is any integer value.
     * @return _true_ if the value is an integer; _false_ otherwise.
     */
    fun isInt(): Boolean = unitType() == TYPE_INT

    /**
     * Tests whether the value is a 32-bit integer value, this excludes 64-bit integers, but includes tiny encoding.
     * @return _true_ if the value is a 32-bit integer or less; _false_ otherwise.
     */
    fun isInt32(): Boolean = unitType() == TYPE_INT && leadIn() != ENC_MIXED_SCALAR_INT64

    /**
     * Tests whether the value is a 64-bit integer value or less.
     * @return _true_ if the value is an 64-bit integer; _false_ otherwise.
     */
    fun isInt64(): Boolean = unitType() == TYPE_INT

    /**
     * Read the current unit as integer, limited to 32-bit.
     * @param alternative The value to return if the current unit is no 32-bit integer.
     * @return The value read or [alternative].
     */
    fun readInt32(alternative: Int = -1): Int {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            ENC_TINY -> when (leadIn and ENC_TINY_MASK) {
                ENC_TINY_INT -> (leadIn shl 27) shr 27
                else -> alternative
            }

            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> view().getInt8(_offset + 1).toInt()
                ENC_MIXED_SCALAR_INT16 -> view().getInt16(_offset + 1).toInt()
                ENC_MIXED_SCALAR_INT32 -> view().getInt32(_offset + 1)
                else -> alternative
            }

            else -> alternative
        }
    }

    /**
     * Read the current unit as integer.
     * @return The value read or _null_, if the current unit is no integer.
     */
    fun readInt64(): BigInt64? {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            ENC_TINY -> when (leadIn and ENC_TINY_MASK) {
                ENC_TINY_INT -> BigInt64((leadIn shl 27) shr 27)
                else -> null
            }

            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> BigInt64(view().getInt8(_offset + 1).toInt())
                ENC_MIXED_SCALAR_INT16 -> BigInt64(view().getInt16(_offset + 1).toInt())
                ENC_MIXED_SCALAR_INT32 -> BigInt64(view().getInt32(_offset + 1))
                ENC_MIXED_SCALAR_INT64 -> view().getBigInt64(_offset + 1)
                else -> null
            }

            else -> null
        }
    }

    fun isTimestamp(): Boolean = unitType() == TYPE_TIMESTAMP

    /**
     * Reads the timestamp if the current unit is a timestamp.
     * @throws IllegalStateException If the current unit is no timestamp.
     */
    fun readTimestamp(): BigInt64 {
        val view = view()
        check(unitType() == TYPE_TIMESTAMP) { "Can't read timestamp, unit is ${unitTypeName(unitType())}" }
        val hi = (view.getInt16(_offset + 1).toLong() and 0xffff) shl 32
        return BigInt64(hi or (view.getInt32(_offset + 3).toLong() and 0xffff_ffff))
    }

    /**
     * Tests whether the number is a 32-bit floating point number or less, returns _false_ for 64-bit or 128-bit floating point numbers.
     * @return _true_ if the unit is a floating point number with a maximum precision of 32-bit; _false_ otherwise.
     */
    fun isFloat32(): Boolean = unitType() == TYPE_FLOAT
            && leadIn() != ENC_MIXED_SCALAR_FLOAT64
            && leadIn() != ENC_MIXED_SCALAR_FLOAT128

    /**
     * Tests whether the number is a 64-bit floating point number, returns _false_ for 128-bit floating point numbers.
     * @return _true_ if the unit is a floating point number with a maximum precision of 64-bit; _false_ otherwise.
     */
    fun isFloat64(): Boolean = unitType() == TYPE_FLOAT
            && leadIn() != ENC_MIXED_SCALAR_FLOAT128

    /**
     * Tests whether the number is any floating point number.
     * @return _true_ if the unit is any floating point number; _false_ otherwise.
     */
    fun isFloat(): Boolean = unitType() == TYPE_FLOAT

    /**
     * Tests if the current unit is an integer or floating point number.
     * @return _true_ if the unit is any integer or floating point number; _false_ otherwise.
     */
    fun isNumber(): Boolean = isInt() || isFloat()

    /**
     * Read a float from the current position. Fails and returns the alternative, when the value is not convertible into a float.
     *
     * @param alternative The value to return, when the value can't be read as a float.
     * @param readStrict If set to _true_, the method does not lose precision, rather returns the alternative.
     * @return The read floating point number or the given alternative.
     */
    fun readFloat32(alternative: Float = Float.NaN, readStrict: Boolean = false): Float {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            // We do not care if this is an integer or float, we just read it as float
            ENC_TINY -> ((leadIn shl 27) shr 27).toFloat()
            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> view().getInt8(_offset + 1).toFloat()
                ENC_MIXED_SCALAR_INT16 -> view().getInt16(_offset + 1).toFloat()
                ENC_MIXED_SCALAR_INT32 -> view().getInt32(_offset + 1).toFloat()
                ENC_MIXED_SCALAR_INT64 -> if (!readStrict) view().getBigInt64(_offset + 1).toFloat() else alternative
                ENC_MIXED_SCALAR_FLOAT32 -> view().getFloat32(_offset + 1)
                ENC_MIXED_SCALAR_FLOAT64 -> if (!readStrict) view().getFloat64(_offset + 1).toFloat() else alternative
                else -> alternative
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
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            // We do not care if this is an integer or float, we just read it as float
            ENC_TINY -> ((leadIn shl 27) shr 27).toDouble()
            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> view().getInt8(_offset + 1).toDouble()
                ENC_MIXED_SCALAR_INT16 -> view().getInt16(_offset + 1).toDouble()
                ENC_MIXED_SCALAR_INT32 -> view().getInt32(_offset + 1).toDouble()
                ENC_MIXED_SCALAR_INT64 -> if (!readStrict) view().getBigInt64(_offset + 1).toDouble() else alternative
                ENC_MIXED_SCALAR_FLOAT32 -> view().getFloat32(_offset + 1).toDouble()
                ENC_MIXED_SCALAR_FLOAT64 -> if (!readStrict) view().getFloat64(_offset + 1) else alternative
                else -> alternative
            }

            else -> alternative
        }
    }

    private var _string: String? = null
    private var _stringOffset = -1
    private var _stringSb: StringBuilder? = null

    /**
     * Uses an internal string reader to parse the string at the current [getOffset] and return it.
     * @return The parsed string.
     * @throws IllegalStateException If the current unit is no string.
     */
    fun readString(): String {
        val leadIn = leadIn()
        check((leadIn and ENC_MASK) == ENC_STRING) { "Current unit is not of type string: ${unitTypeName(unitType())}" }
        if (_offset != _stringOffset) {
            val view = view()
            val leadInOffset = _offset
            val sb = _stringSb ?: StringBuilder()
            try {
                val size = unitPayloadSize()
                addOffset(unitHeaderSize())
                val end = _offset + size
                sb.setLength(0)
                readSubstring(view, _offset, end, sb, globalDict, localDict)
                _string = sb.toString()
            } finally {
                _stringSb = sb
                _offset = leadInOffset
                _stringOffset = leadInOffset
            }
        }
        val string = _string
        check(string != null) { "Failed to decode string" }
        return string
    }

    /**
     * Tests if the current unit is a reference.
     * @return _true_ if the current unit is a reference.
     */
    fun isRef(): Boolean = unitType() == TYPE_REF

    /**
     * Tests whether the current unit is a reference into the local dictionary.
     * @return _true_ if the current unit is a reference into the local dictionary.
     */
    fun isLocalRef(): Boolean {
        val leadIn = leadIn()
        if ((leadIn and ENC_MASK) == ENC_MIXED) {
            return when (leadIn and ENC_MIXED_MASK) {
                ENC_MIXED_REF5_LOCAL -> true
                ENC_MIXED_REF -> (leadIn and ENC_MIXED_REF_GLOBAL_BIT) != ENC_MIXED_REF_GLOBAL_BIT
                else -> false
            }
        }
        return false
    }

    /**
     * Tests whether the current unit is a reference into the global dictionary.
     * @return _true_ if the current unit is a reference into the global dictionary.
     */
    fun isGlobalRef(): Boolean {
        val leadIn = leadIn()
        if ((leadIn and ENC_MASK) == ENC_MIXED) {
            return when (leadIn and ENC_MIXED_MASK) {
                ENC_MIXED_REF5_GLOBAL -> true
                ENC_MIXED_REF -> (leadIn and ENC_MIXED_REF_GLOBAL_BIT) == ENC_MIXED_REF_GLOBAL_BIT
                else -> false
            }
        }
        return false
    }

    /**
     * Read the value of the reference, not returning the global- or back-bits.
     * @return The value of the reference or _-1_, if being a null-reference or invalid.
     */
    fun readRef(): Int {
        val leadIn = leadIn()
        if ((leadIn and ENC_MASK) == ENC_MIXED) {
            return when (leadIn and ENC_MIXED_MASK) {
                ENC_MIXED_REF5_LOCAL, ENC_MIXED_REF5_GLOBAL -> leadIn and 0xf
                ENC_MIXED_REF -> when (leadIn) {
                    ENC_MIXED_REF_INT8 -> (view().getInt8(_offset + 1).toInt() and 0xff) + 16
                    ENC_MIXED_REF_INT16 -> (view().getInt16(_offset + 1).toInt() and 0xffff) + 16
                    ENC_MIXED_REF_INT32 -> view().getInt32(_offset + 1) + 16
                    // ENC_MIXED_REF_NULL
                    else -> -1
                }

                else -> -1
            }
        }
        return -1
    }

    /**
     * Tests if the current [offset] is at the lead-in of a dictionary.
     * @return _true_ if the current type is a dictionary; _false_ otherwise.
     */
    fun isDictionary(): Boolean = unitType() == TYPE_DICTIONARY

    /**
     * Tests if the current [offset] is at the lead-in of a map.
     * @return _true_ if the current type is a dictionary; _false_ otherwise.
     */
    fun isMap(): Boolean = unitType() == TYPE_MAP

    /**
     * Tests if the current [offset] is at the lead-in of an array.
     * @return _true_ if the current type is an array; _false_ otherwise.
     */
    fun isArray(): Boolean = unitType() == TYPE_ARRAY

    /**
     * Test if the current offset is at the lead-in of an XYZ special.
     * @return true if the current offset is at the lead-in of an XYZ special; false otherwise.
     */
    fun isXyz(): Boolean = unitType() == TYPE_XYZ

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