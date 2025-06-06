@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport

/**
 * The interface for all [GeoJSON coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 * @since 3.0
 */
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

    /**
     * Recursively fix all coordinates and their components, so that all values are doubles with 7 decimal digits precision.
     *
     * Holes _(`null` elements)_ in the coordinates are removed, except for `longitude` and `latitude`.
     *
     * - Throws [NakshaError.ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if any component is invalid and can't be fixed.
     * @since 3.0
     */
    fun fix(): ICoordinates
}
