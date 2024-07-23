package naksha.geo

import kotlin.js.JsExport

/**
 * The interface for all GeoJSON coordinates.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface ICoordinates {
    fun calculateBBox(): BoundingBoxProxy
}
