package naksha.geo

import kotlin.js.JsExport

/**
 * The interface for all GeoJSON coordinates.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface ICoordinates {
    /**
     * Tests if the coordinates have at least one point with `Z`-ordinate.
     * @return `true` if the coordinates have at least one point with `Z`-ordinate; `false` otherwise.
     * @since 3.0
     */
    fun hasZ(): Boolean

    /**
     * Tests if the coordinates have at least one point with an `M`-ordinate.
     * @return `true` if the coordinates have at least one point with an `M`-ordinate; `false` otherwise.
     * @since 3.0
     */
    fun hasM(): Boolean
}
