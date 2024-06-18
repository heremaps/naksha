package naksha.jbon

import naksha.base.Binary
import naksha.base.BinaryView
import kotlin.js.JsExport

/**
 * An array implementation.
 * @constructor Create a new JBON array reader.
 * @param binary The binary to map initially.
 * @param pos The position of the first byte to access, defaults to `binary.pos`.
 * @param end The first byte that should not be read, defaults to `binary.end`.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbArray(binary: BinaryView = Binary.EMPTY_IMMUTABLE, pos: Int = binary.pos, end: Int = binary.end)
    : JbEntryArray<JbArray>(binary, pos, end) {

    override fun parseHeader() {
        check(unitType == TYPE_ARRAY) { "Mapped structure is no array, but ${JbReader.unitTypeName(unitType)}" }
        index = -1
        length = if (bodySize() == 0) 0 else Int.MAX_VALUE
    }

    override fun nextEntry(): Boolean {
        if (reader.pos < end) {
            reader.nextUnit()
            return reader.pos < end
        }
        return false
    }

    override fun loadEntry() {
    }

    override fun dropEntry() {
    }

    /**
     * Returns the reader, being positioned at the value of the entry.
     * @return The reader, positioned at the value of the entry.
     * @throws IllegalStateException If the position is invalid.
     */
    fun value(): JbReader {
        check(index >= 0)
        return reader
    }

}