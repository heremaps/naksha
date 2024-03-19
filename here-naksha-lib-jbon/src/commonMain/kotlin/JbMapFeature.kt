@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A feature that wraps a map.
 */
@JsExport
open class JbMapFeature : JbFeature() {
    private lateinit var _map: JbMap

    override fun clear(): JbMapFeature {
        super.clear()
        if (this::_map.isInitialized) _map.clear()
        return this
    }

    override fun parseHeader() {
        super.parseHeader()
        check(reader.isMap()) {"Failed to parse feature payload, expected map, but found ${JbReader.unitTypeName(reader.unitType())}"}
        if (!this::_map.isInitialized) _map = JbMap()
        _map.mapReader(reader)
    }

    /**
     * Returns the reader for the embedded map.
     * @return The map reader of root.
     */
    open fun root() : JbMap = _map
}