package naksha.jbon

import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * A feature is a record, where the root unit is a map.
 * @constructor Create a new feature reader for records with a map as body.
 * @param dictManager the dictionary manager to use.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class JbFeatureDecoder(dictManager: IDictManager? = null) : JbRecordDecoder(dictManager) {
    private lateinit var _map: JbMapDecoder

    override fun clear(): JbFeatureDecoder {
        super.clear()
        if (this::_map.isInitialized) _map.clear()
        return this
    }

    override fun parseHeader() {
        super.parseHeader()
        check(reader.isMap()) { "Failed to parse feature payload, expected map, but found ${JbDecoder.unitTypeName(reader.unitType())}" }
        if (!this::_map.isInitialized) _map = JbMapDecoder()
        _map.mapReader(reader)
    }

    /**
     * Returns the reader for the embedded map.
     * @return The map reader of root.
     */
    open fun root(): JbMapDecoder = _map

    /**
     * Decode the feature into a map.
     * @return the map.
     */
    open fun toAnyObject(): AnyObject {
        val feature = root().toAnyObject()
        val id = id()
        if (id != null && "id" !in feature) feature.setRaw("id", id)
        return feature
    }

    /**
     * Moves the cursor to given path.
     *
     * If the select succeeds, [`decoder.reader.unitType()`][naksha.jbon.JbDecoder.unitType] can be used to detect what the value is, or [`decoder.reader.decodeValue()`][naksha.jbon.JbDecoder.decodeValue] can be used to simply decode the value. Beware, when the value is a complex type (Map, Array), decoding is more expensive than maybe necessary, so maybe it is better to just test the unit-type!
     *
     * @param path the path to select, strings are used to enter maps, integers are used to select from arrays.
     * @return _true_ if the path was selected, and exists; _false_ otherwise.
     */
    open fun selectPath(vararg path: Any): Boolean {
        reset() // Move the reader to the root-map.
        return _selectPath(reader, 0, path)
    }

    private tailrec fun _selectPath(r: JbDecoder, i: Int, path: Array<out Any>): Boolean {
        if (i >= path.size) return true
        val pkey = path[i]
        @Suppress("CascadeIf")
        if (pkey is String) {
            if (r.unitType() != TYPE_MAP) return false
            val end = r.pos + r.unitSize()
            r.enterStruct()
            while (r.pos < end) {
                // Keys are always dictionary references.
                val index = r.decodeRef()
                val dict = if (r.isGlobalRef()) r.globalDict else r.localDict
                check(dict != null) { "Missing dictionary for key-reference: $index" }
                val key = dict.get(index)
                // Skip over key
                r.nextUnit()
                // If the key was what we wanted
                if (pkey == key) return _selectPath(r, i + 1, path)
                // Otherwise, skip value as well
                r.nextUnit()
            }
            // Not found
            return false
        } else if (pkey is Int) {
            if (r.unitType() != TYPE_ARRAY) return false
            val end = r.pos + r.unitSize()
            r.enterStruct()
            var index = 0
            while (r.pos < end) {
                if (pkey == index) return _selectPath(r, i + 1, path)
                // Skip over value
                r.nextUnit()
                index++
            }
            // Not found
            return false
        } else return false
    }
}