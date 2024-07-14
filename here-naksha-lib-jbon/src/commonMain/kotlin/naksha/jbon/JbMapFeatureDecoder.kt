package naksha.jbon

import kotlin.js.JsExport

/**
 * A feature with the root unit being a map.
 * @constructor Create a new JBON feature reader for features with a map as body.
 * @param dictManager The dictionary manager to use.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class JbMapFeatureDecoder(dictManager: IDictManager? = null) : JbFeatureDecoder(dictManager) {
    private lateinit var _map: JbMapDecoder

    override fun clear(): JbMapFeatureDecoder {
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