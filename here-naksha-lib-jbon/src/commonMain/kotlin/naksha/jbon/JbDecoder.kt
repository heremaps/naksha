@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.*
import naksha.base.Binary.BinaryCompanion.EMPTY_IMMUTABLE
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A low level JBON reader that can be used with any binary.
 * @constructor Creates a new reader.
 */
@Suppress("DuplicatedCode", "PropertyName")
@JsExport
open class JbDecoder {

    @OptIn(ExperimentalJsStatic::class)
    companion object JbDecoderCompanion {
        /**
         * Returns the human-readable name for the given unit-type.
         * @param unitType The unit-type as returned by [unitType].
         * @return The human-readable name of this unit-type.
         */
        @JvmStatic
        @JsStatic
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
        @JsStatic
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
        private fun readCodePoint(view: BinaryView, offset: Int, end: Int): Int {
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
        internal fun readSubstring(
            view: BinaryView,
            offset: Int,
            end: Int,
            sb: StringBuilder,
            globalDict: JbDictDecoder? = null,
            localDict: JbDictDecoder? = null
        ) {
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
         * @param jbMapDecoder The JBON map.
         * @return The platform native map.
         */
        @JvmStatic
        internal fun readMap(jbMapDecoder: JbMapDecoder): AnyObject {
            val imap = AnyObject()
            while (jbMapDecoder.next()) {
                imap[jbMapDecoder.key()] = jbMapDecoder.value().decodeValue()
            }
            return imap
        }

        /**
         * Convert the given JBON array into a platform native array.
         * @param jbArray The JBON array.
         * @return The platform native array.
         */
        @JvmStatic
        internal fun readArray(jbArray: JbArrayDecoder): Array<Any?> {
            val arr = Array<Any?>(jbArray.length()) {}
            var i = 0
            while (jbArray.next() && jbArray.ok()) {
                arr[i] = jbArray.value().decodeValue()
                i += 1
            }
            return arr
        }
    }

    /**
     * The binary that is read. When assigned, by default the
     */
    var binary: BinaryView = EMPTY_IMMUTABLE
        set(value) {
            field = value
            this.end = value.end
            this.pos = value.pos
        }

    /**
     * The local dictionary to be used when decoding text or references.
     */
    var localDict: JbDictDecoder? = null

    /**
     * The global dictionary to be used when decoding text or references.
     */
    var globalDict: JbDictDecoder? = null

    /**
     * The current offset in the binary, can't become bigger than [end].
     */
    var pos = 0
        set(value) {
            field = if (value < 0) 0 else if (value >= end) end else value
            _unitTypeOffset = -1
            _headerSizeOffset = -1
            _unitPayloadSizeOffset = -1
            _stringOffset = -1
        }

    /**
     * The current end of the view, can become bigger than `binary`
     */
    var end: Int = 0
        set(value) {
            field = if (value < 0) 0 else if (value >= binary.byteLength) binary.byteLength else value
            _unitTypeOffset = -1
            _headerSizeOffset = -1
            _unitPayloadSizeOffset = -1
            _stringOffset = -1
        }

    /**
     * Move the [pos] to the `binary.pos` and [end] to `binary.end`. Clears the local and global dictionaries (_null_).
     * @return this.
     */
    open fun clear() {
        end = binary.end
        pos = binary.pos
        localDict = null
        globalDict = null
    }

    /**
     * Move the [pos] to the `binary.pos`, leaves [end] unchanged.
     * @return this.
     */
    open fun reset() {
        pos = 0
    }

    /**
     * Maps the given binary.
     * @param binary The binary to map.
     * @param pos The position to start reading at; defaults to `binary.pos`.
     * @param end The end to map; defaults to `binary.end`.
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     * @return this.
     */
    open fun mapBinary(binary: BinaryView, pos: Int = binary.pos, end: Int = binary.end, localDict: JbDictDecoder? = null, globalDict: JbDictDecoder? = null): JbDecoder {
        check(pos <= end)
        clear()
        this.binary = binary
        this.localDict = localDict
        this.globalDict = globalDict
        this.end = end
        this.pos = pos
        return this
    }

    /**
     * Maps the given foreign reader into this reader. Will share the global and local dictionaries, but not caches or offset.
     * @param reader The reader to map.
     */
    open fun mapReader(reader: JbDecoder) {
        clear()
        this.binary = reader.binary
        this.localDict = reader.localDict
        this.globalDict = reader.globalDict
        this.end = reader.end
        this.pos = reader.pos
    }

    /**
     * Creates a view above the given byte-array and maps this view for reading. This sets [pos] to `0` and [end] to [length],
     * which allows to invoke [reset] or [clear] in a deterministic way.
     * @param bytes The byte-array to map.
     * @param offset The offset in the byte-array to create the view for.
     * @param length The amount of byte to map.
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     */
    open fun mapBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size, localDict: JbDictDecoder? = null, globalDict: JbDictDecoder? = null) {
        clear()
        this.binary = Binary(bytes, offset, length)
        this.localDict = localDict
        this.globalDict = globalDict
    }

    /**
     * Tests if the given offset is valid.
     * @param offset The offset to test for.
     * @return true if the offset can be read.
     */
    fun isValid(offset: Int): Boolean = offset in pos..<end

    /**
     * Tests whether the reader is end-of-file (at a not readable position).
     * @return _true_ if the reader is invalid (can't be read from); _false_ if ready for reading.
     */
    fun eof(): Boolean = pos < 0 || pos >= end

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
        pos += unitSize()
        return pos < end
    }

    /**
     * Reads the lead-in byte from the current [pos]. If [eof], the methods returns the lead-in byte for [TYPE_UNDEFINED].
     * @return The lead-in from the current [pos].
     */
    open fun leadIn(): Int {
        val offset = this.pos
        if (offset < 0 || offset >= end) return ENC_MIXED_CONST_UNDEFINED
        return binary.getInt8(offset).toInt() and 0xff
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
        if (pos != _unitTypeOffset) {
            val leadIn = leadIn()
            _unitTypeOffset = pos
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
        if (pos != _headerSizeOffset) {
            val leadIn = leadIn()
            _headerSizeOffset = pos
            _headerSize = unitHeaderSize(leadIn)
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
        if (pos != _unitPayloadSizeOffset) {
            val leadIn = leadIn()
            val view = binary
            val offset = pos
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
     * Returns the size of the current unit in byte including the unit header, therefore the amount of byte to add to [pos]
     * to skip the current unit and seek to the next one. The method return 0, when [eof].
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
                ENC_STRUCT_VARIANT8 -> binary.getInt8(pos + skipSize).toInt() and 0xff
                ENC_STRUCT_VARIANT16 -> binary.getInt16(pos + skipSize).toInt() and 0xffff
                ENC_STRUCT_VARIANT32 -> binary.getInt32(pos + skipSize)
                // ENC_STRUCT_VARIANT0
                else -> null
            }
        }
        return null
    }

    /**
     * Move the [pos] into the unit, skipping the lead-in byte and the (optional) size field.
     * @return _True_ if the unit was entered successfully; _false_ if the current unit can't be entered.
     */
    fun enterStruct(): Boolean {
        val leadIn = leadIn()
        val baseType = leadIn and ENC_MASK
        if (baseType == ENC_STRUCT) {
            pos += unitHeaderSize()
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
    fun decodeInt32(alternative: Int = -1): Int {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            ENC_TINY -> when (leadIn and ENC_TINY_MASK) {
                ENC_TINY_INT -> (leadIn shl 27) shr 27
                else -> alternative
            }

            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> binary.getInt8(pos + 1).toInt()
                ENC_MIXED_SCALAR_INT16 -> binary.getInt16(pos + 1).toInt()
                ENC_MIXED_SCALAR_INT32 -> binary.getInt32(pos + 1)
                else -> alternative
            }

            else -> alternative
        }
    }

    /**
     * Read the current unit as integer.
     * @return The value read or _null_, if the current unit is no integer.
     */
    fun decodeInt64(): Int64? {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            ENC_TINY -> when (leadIn and ENC_TINY_MASK) {
                ENC_TINY_INT -> Int64((leadIn shl 27) shr 27)
                else -> null
            }

            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> Int64(binary.getInt8(pos + 1).toInt())
                ENC_MIXED_SCALAR_INT16 -> Int64(binary.getInt16(pos + 1).toInt())
                ENC_MIXED_SCALAR_INT32 -> Int64(binary.getInt32(pos + 1))
                ENC_MIXED_SCALAR_INT64 -> binary.getInt64(pos + 1)
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
    fun decodeTimestamp(): Int64 {
        val view = binary
        check(unitType() == TYPE_TIMESTAMP) { "Can't read timestamp, unit is ${unitTypeName(unitType())}" }
        val hi = (view.getInt16(pos + 1).toLong() and 0xffff) shl 32
        return Int64(hi or (view.getInt32(pos + 3).toLong() and 0xffff_ffff))
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
    fun decodeFloat32(alternative: Float = Float.NaN, readStrict: Boolean = false): Float {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            // We do not care if this is an integer or float, we just read it as float
            ENC_TINY -> ((leadIn shl 27) shr 27).toFloat()
            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> binary.getInt8(pos + 1).toFloat()
                ENC_MIXED_SCALAR_INT16 -> binary.getInt16(pos + 1).toFloat()
                ENC_MIXED_SCALAR_INT32 -> binary.getInt32(pos + 1).toFloat()
                ENC_MIXED_SCALAR_INT64 -> if (!readStrict) binary.getInt64(pos + 1).toFloat() else alternative
                ENC_MIXED_SCALAR_FLOAT32 -> binary.getFloat32(pos + 1)
                ENC_MIXED_SCALAR_FLOAT64 -> if (!readStrict) binary.getFloat64(pos + 1).toFloat() else alternative
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
    fun decodeFloat64(alternative: Double = Double.NaN, readStrict: Boolean = false): Double {
        val leadIn = leadIn()
        return when (leadIn and ENC_MASK) {
            // We do not care if this is an integer or float, we just read it as float
            ENC_TINY -> ((leadIn shl 27) shr 27).toDouble()
            ENC_MIXED -> when (leadIn) {
                ENC_MIXED_SCALAR_INT8 -> binary.getInt8(pos + 1).toDouble()
                ENC_MIXED_SCALAR_INT16 -> binary.getInt16(pos + 1).toDouble()
                ENC_MIXED_SCALAR_INT32 -> binary.getInt32(pos + 1).toDouble()
                ENC_MIXED_SCALAR_INT64 -> if (!readStrict) binary.getInt64(pos + 1).toDouble() else alternative
                ENC_MIXED_SCALAR_FLOAT32 -> binary.getFloat32(pos + 1).toDouble()
                ENC_MIXED_SCALAR_FLOAT64 -> if (!readStrict) binary.getFloat64(pos + 1) else alternative
                else -> alternative
            }

            else -> alternative
        }
    }

    private var _string: String? = null
    private var _stringOffset = -1
    private var _stringSb: StringBuilder? = null

    /**
     * Uses an internal string reader to parse the string at the current [pos] and return it.
     * @return The parsed string.
     * @throws IllegalStateException If the current unit is no string.
     */
    fun decodeString(): String {
        val leadIn = leadIn()
        check((leadIn and ENC_MASK) == ENC_STRING) { "Current unit is not of type string: ${unitTypeName(unitType())}" }
        if (pos != _stringOffset) {
            val view = binary
            val leadInOffset = pos
            val sb = _stringSb ?: StringBuilder()
            try {
                val size = unitPayloadSize()
                pos += unitHeaderSize()
                val end = pos + size
                sb.setLength(0)
                readSubstring(view, pos, end, sb, globalDict, localDict)
                _string = sb.toString()
            } finally {
                _stringSb = sb
                pos = leadInOffset
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
    fun decodeRef(): Int {
        val leadIn = leadIn()
        if ((leadIn and ENC_MASK) == ENC_MIXED) {
            return when (leadIn and ENC_MIXED_MASK) {
                ENC_MIXED_REF5_LOCAL, ENC_MIXED_REF5_GLOBAL -> leadIn and 0xf
                ENC_MIXED_REF -> when (leadIn) {
                    ENC_MIXED_REF_INT8 -> (binary.getInt8(pos + 1).toInt() and 0xff) + 16
                    ENC_MIXED_REF_INT16 -> (binary.getInt16(pos + 1).toInt() and 0xffff) + 16
                    ENC_MIXED_REF_INT32 -> binary.getInt32(pos + 1) + 16
                    // ENC_MIXED_REF_NULL
                    else -> -1
                }

                else -> -1
            }
        }
        return -1
    }

    /**
     * Tests if the current [pos] is at the lead-in of a dictionary.
     * @return _true_ if the current type is a dictionary; _false_ otherwise.
     */
    fun isDictionary(): Boolean = unitType() == TYPE_DICTIONARY

    /**
     * Tests if the current [pos] is at the lead-in of a map.
     * @return _true_ if the current type is a dictionary; _false_ otherwise.
     */
    fun isMap(): Boolean = unitType() == TYPE_MAP

    /**
     * Tests if the current [pos] is at the lead-in of an array.
     * @return _true_ if the current type is an array; _false_ otherwise.
     */
    fun isArray(): Boolean = unitType() == TYPE_ARRAY

    /**
     * Test if the current offset is at the lead-in of an XYZ special.
     * @return true if the current offset is at the lead-in of an XYZ special; false otherwise.
     */
    fun isXyz(): Boolean = unitType() == TYPE_XYZ

    /**
     * Read the current unit as _null_, [Boolean], [Int], [Int64], [Double], [String], [JbMapDecoder], [JbArrayDecoder] or [Array].
     * @return the current unit as _null_, [Boolean], [Int], [Int64], [Double], [String], [JbMapDecoder], [JbArrayDecoder] or [Array].
     * @throws IllegalStateException If the reader position or the unit-type is invalid.
     */
    fun decodeValue(): Any? {
        return if (isInt32()) {
            decodeInt32()
        } else if (isInt()) {
            decodeInt64()
        } else if (isString()) {
            decodeString()
        } else if (isBool()) {
            readBoolean()
        } else if (isFloat64() || isFloat32()) {
            decodeFloat64()
        } else if (isNull()) {
            null
        } else if (isTimestamp()) {
            decodeTimestamp()
        } else if (isMap()) {
            readMap(JbMapDecoder().mapReader(this))
        } else if (isArray()) {
            readArray(JbArrayDecoder().mapReader(this))
        } else {
            throw IllegalStateException("Not implemented jbon value type")
        }
    }
}