@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.BinaryView
import naksha.base.Binary
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The base class for all structure readers (JBON structure types). A structure does always have the unit-header. Apart from this, the
 * structure may have values that should be parsed ones when mapped, and other values, that should only be read on demand. For this
 * purpose the [parseHeader] method can be overridden.
 * @constructor Create a new structure reader.
 */
@Suppress("UNCHECKED_CAST", "MemberVisibilityCanBePrivate")
@JsExport
abstract class JbStructDecoder<SELF : JbStructDecoder<SELF>> {

    /**
     * The decoder used to read from the structure.
     */
    val reader = JbDecoder()

    /**
     * The lead-in byte of the structure.
     */
    internal var leadIn: Int = 0

    /**
     * The unit-type, read from lead-in.
     */
    internal var unitType: Int = 0

    /**
     * The start of the header of the mapped structure.
     */
    internal var start: Int = 0

    /**
     * The variant; if any.
     */
    internal var variant: Int? = null

    /**
     * The start of the structure body, the first byte to read of the content. If being the same as [end], then the structure
     * does not have any lazy read body.
     */
    internal var bodyStart: Int = 0

    /**
     * The end of the structure, the first byte that must not be read.
     */
    internal var end: Int = 0

    /**
     * Returns the structure variant.
     * @return The variant, if any; otherwise _null_.
     */
    fun variant(): Int? = variant

    /**
     * Invoked after a view, reader or bytes were mapped to parse the internal structure header. When the method is called, the [reader]
     * will be placed behind the unit header, so at the first payload byte of the structure. If the method does nothing, the payload
     * will be fully mapped as body.
     */
    protected abstract fun parseHeader()

    /**
     * Map a specific region of a binary as object.
     *
     * @param binary The binary to map.
     * @param leadInOffset The offset where the structure starts (lead-in byte of header).
     * @param localDict The local dictionary to use, if any.
     * @param globalDict The global dictionary to use, if any.
     * @return this.
     */
    protected open fun map(binary: BinaryView, leadInOffset: Int, localDict: JbDictionary?, globalDict: JbDictionary?): SELF {
        clear()
        reader.mapBinary(binary, leadInOffset, binary.end, localDict, globalDict)
        check(reader.isStruct()) { "Mapping failed, the view does not contain a structure at the given offset" }
        leadIn = reader.leadIn()
        variant = reader.unitVariant()
        unitType = reader.unitType()
        start = leadInOffset
        end = leadInOffset + reader.unitSize()
        reader.end = end
        reader.enterStruct()
        parseHeader()
        bodyStart = reader.pos
        return this as SELF
    }

    /**
     * Returns the local dictionary or throws an [IllegalStateException].
     * @return The local dictionary.
     */
    fun localDict(): JbDictionary {
        val localDict = reader.localDict
        check(localDict != null)
        return localDict
    }

    /**
     * Clear the mapper, drops the view, the mapper becomes invalid.
     * @return this.
     */
    open fun clear(): SELF {
        reader.clear()
        leadIn = 0
        start = 0
        bodyStart = 0
        end = 0
        variant = null
        return this as SELF
    }

    /**
     * Resets the mapper to the body start, to parse body parts.
     * @return this.
     */
    open fun reset(): SELF {
        reader.pos = bodyStart
        return this as SELF
    }

    /**
     * Map a structure from the given binary. The [offset] should refer to the lead-in byte.
     *
     * @param binary The binary to map.
     * @param offset The offset where the structure starts (lead-in byte).
     * @param localDict The local dictionary to map, if any.
     * @param globalDict The global dictionary to use, if any.
     * @return this.
     */
    fun mapBinary(binary: BinaryView, offset: Int = 0, localDict: JbDictionary? = null, globalDict: JbDictionary? = null): SELF {
        map(binary, offset, localDict, globalDict)
        return this as SELF
    }

    /**
     * When called, this method will map the given byte-array, automatically creating a [Binary] for it, and detect the
     * [bodyStart] and [end] from the header stored in the bytes. May additionally do other header processing.
     * @param bytes The bytes to map.
     * @param start The offset of the lead-in byte of the byte-array.
     * @param end The offset of the first byte not to map.
     * @return this.
     */
    fun mapBytes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): SELF {
        map(Binary(bytes, start, end), 0, null, null)
        return this as SELF
    }

    /**
     * Maps the view from the given reader. Verifies that the reader is at a lead-in byte and reads the header to
     * detect [bodyStart] and [end].
     * @param reader The reader from which to use the view and offset.
     * @return this.
     */
    fun mapReader(reader: JbDecoder): SELF {
        map(reader.binary, reader.pos, reader.localDict, reader.globalDict)
        return this as SELF
    }

    /**
     * Returns the size of the body (amount of byte).
     */
    fun bodySize(): Int {
        return end - bodyStart
    }

    /**
     * Returns the total size (amount of byte) being mapped (including the lead-in, header and other not body parts).
     */
    fun totalSize(): Int {
        return end - start
    }
}