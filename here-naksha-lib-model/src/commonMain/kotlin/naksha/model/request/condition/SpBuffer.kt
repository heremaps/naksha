@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Computes a POLYGON or MULTIPOLYGON that represents all points whose distance from a geometry/geography is less than or equal to a given distance. A negative distance shrinks the geometry rather than expanding it. A negative distance may shrink a polygon completely, in which case POLYGON EMPTY is returned. For points and lines negative distances always return empty results.
 *
 * This is based upon the [PostGIS definition](https://postgis.net/docs/ST_Buffer.html).
 *
 * @property distance the distance to expand or shrink the client geometry.
 * @property geography for geography (_true_), the distance is specified in meters. For geometry (_false_), the distance is specified in the units of the Spatial Reference System of the geometry.
 * @property quad_segs number of line segments used to approximate a quarter circle (default is 8).
 * @property join join style (defaults to "round"). 'miter' is accepted as a synonym for 'mitre'.
 * @property endCap endcap style (defaults to "round"). 'butt' is accepted as a synonym for 'flat'.
 * @property side 'left' or 'right' performs a single-sided buffer on the geometry, with the buffered side relative to the direction of the line. This is only applicable to LINESTRING geometry and does not affect POINT or POLYGON geometries. By default, end caps are square.
 * @param childTransformation an optional child transformation to be executed before this transformation is applied.
 */
@JsExport
class SpBuffer(
    @JvmField
    val distance: Double,
    @JvmField
    val geography: Boolean = true,
    @JvmField
    val quad_segs: QuadSegs? = null,
    @JvmField
    val join: Join? = null,
    @JvmField
    val endCap: EndCap? = null,
    @JvmField
    val side: Side? = null,
    childTransformation: SpTransformation? = null
) : SpTransformation(childTransformation) {
    /**
     * Buffer parameter.
     * @property segments number of line segments used to approximate a quarter circle (default is 8).
     */
    data class QuadSegs(val segments: Int = 8)

    /**
     * Buffer parameter.
     * @property style join style (defaults to "round"). 'miter' is accepted as a synonym for 'mitre'.
     * @property limit the optional mitre ratio limit.
     */
    data class Join(val style: String = ROUND, val limit: Double? = null) {
        companion object JoinCompanion {
            const val ROUND = "round"
            const val MITRE = "mitre"
            const val BEVEL = "bevel"
        }
    }

    /**
     * Buffer parameter.
     * @property style endcap style (defaults to "round"). 'butt' is accepted as a synonym for 'flat'.
     */
    data class EndCap(val style: String = ROUND) {
        companion object EndCapCompanion {
            const val ROUND = "round"
            const val BUTT = "butt"
            const val FLAT = "flat"
        }
    }

    /**
     * Buffer parameter.
     * @property style 'left' or 'right' performs a single-sided buffer on the geometry, with the buffered side relative to the direction of the line. This is only applicable to LINESTRING geometry and does not affect POINT or POLYGON geometries. By default, end caps are square.
     */
    data class Side(val style: String = BOTH) {
        companion object SideCompanion{
            const val BOTH = "both"
            const val LEFT = "left"
            const val RIGHT = "right"
        }
    }

}