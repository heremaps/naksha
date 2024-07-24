package naksha.jbon

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
        check(reader.isMap()) {"Failed to parse feature payload, expected map, but found ${JbDecoder.unitTypeName(reader.unitType())}"}
        if (!this::_map.isInitialized) _map = JbMapDecoder()
        _map.mapReader(reader)
    }

    /**
     * Returns the reader for the embedded map.
     * @return The map reader of root.
     */
    open fun root() : JbMapDecoder = _map
}