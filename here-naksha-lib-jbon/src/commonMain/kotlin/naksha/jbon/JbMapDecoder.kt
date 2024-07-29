package naksha.jbon

import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * A map view.
 * @constructor Create a new JBON map reader.
 */
@Suppress("DuplicatedCode", "OPT_IN_USAGE")
@JsExport
class JbMapDecoder : JbEntryArray<JbMapDecoder>() {

    override fun parseHeader() {
        check(unitType == TYPE_MAP) { "Mapped structure is no map, but ${JbDecoder.unitTypeName(unitType)}" }
        valueReader.mapReader(reader)
        index = -1
        key = null
        length = if (bodySize() == 0) 0 else Int.MAX_VALUE
    }

    override fun nextEntry(): Boolean {
        if (reader.pos < end) {
            // Seek over key and value.
            reader.nextUnit()
            reader.nextUnit()
            return reader.pos < end
        }
        return false
    }

    override fun loadEntry() {
        val reader = this.reader
        if (reader.pos != cachedOffset) {
            val vr = this.valueReader
            vr.pos = reader.pos
            check(vr.isRef()) { "Key in map at offset ${reader.pos} is no string-reference" }
            val index = vr.decodeRef()
            val dict = if (vr.isGlobalRef()) vr.globalDict else vr.localDict
            check(dict != null) { (if (vr.isGlobalRef()) "Global" else "Local") + "-Dictionary in map is null, but referred to at index ${reader.pos}" }
            key = dict.get(index)
            check(vr.nextUnit()) {"Failed to seek to value of key $key"}
            // We're now positioned at the value.
        }
    }

    override fun dropEntry() {
        cachedOffset = -1
        key = null
    }

    /**
     * A reader we use flexible, when reading of values is requested.
     */
    private val valueReader = JbDecoder()

    /**
     * The [reader] offset that currently is cached.
     */
    private var cachedOffset: Int = -1

    /**
     * The cached key at the current index, if index is valid.
     */
    private var key: String? = null

    fun key(): String {
        check(index >= 0)
        loadEntry()
        val key = this.key
        check(key != null)
        return key
    }

    fun value(): JbDecoder {
        check(index >= 0)
        loadEntry()
        return valueReader
    }

    /**
     * Searches the map for the given key and if found, select the entry with this key as current position.
     * @param key The key to search for.
     */
    fun selectKey(key: String): Boolean {
        val backup = reader.pos
        if (first()) {
            if (key == key()) return true
            while (next()) {
                if (key == key()) return true
            }
        }
        reader.pos = backup
        return false
    }

    /**
     * Returns this map as [AnyObject].
     * @return This binary as [AnyObject].
     */
    fun toIMap(): AnyObject {
        return JbDecoder.readMap(this)
    }
}